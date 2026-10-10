package com.dcim.rfid.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.common.asset.AssetOpsPort;
import com.dcim.common.csv.CsvSupport;
import com.dcim.common.seq.SeqNoGenerator;
import com.dcim.rfid.entity.RfidBatchEntity;
import com.dcim.rfid.entity.RfidBatchItemEntity;
import com.dcim.rfid.entity.RfidScanRecordEntity;
import com.dcim.rfid.mapper.RfidAssetQueryMapper;
import com.dcim.rfid.mapper.RfidBatchItemMapper;
import com.dcim.rfid.mapper.RfidBatchMapper;
import com.dcim.rfid.mapper.RfidMaterialQueryMapper;
import com.dcim.rfid.mapper.RfidScanRecordMapper;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RfidBatchService {
    private static final Set<String> SOURCE_TYPES = Set.of("采购到货", "RMA返修", "调拨");
    private final RfidBatchMapper batchMapper;
    private final RfidBatchItemMapper itemMapper;
    private final RfidScanRecordMapper scanMapper;
    private final RfidMaterialQueryMapper materialQueryMapper;
    private final RfidAssetQueryMapper assetQueryMapper;
    private final AssetOpsPort assetOpsPort;
    private final SeqNoGenerator seqNoGenerator;
    private final TransactionTemplate scanTx;

    public RfidBatchService(
        RfidBatchMapper batchMapper,
        RfidBatchItemMapper itemMapper,
        RfidScanRecordMapper scanMapper,
        RfidMaterialQueryMapper materialQueryMapper,
        RfidAssetQueryMapper assetQueryMapper,
        AssetOpsPort assetOpsPort,
        SeqNoGenerator seqNoGenerator,
        PlatformTransactionManager txManager
    ) {
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.scanMapper = scanMapper;
        this.materialQueryMapper = materialQueryMapper;
        this.assetQueryMapper = assetQueryMapper;
        this.assetOpsPort = assetOpsPort;
        this.seqNoGenerator = seqNoGenerator;
        this.scanTx = new TransactionTemplate(txManager);
    }

    @Transactional
    public Map<String, Object> create(String name, String supplierCode, String sourceType, String remark) {
        if (name != null && !name.isBlank()) {
            String src = sourceType != null && !sourceType.isBlank() ? sourceType.trim() : "采购到货";
            if (!SOURCE_TYPES.contains(src)) {
                throw BusinessException.badRequest("验收来源无效（仅支持 采购到货/RMA返修/调拨）：" + src);
            } else {
                RfidBatchEntity b = new RfidBatchEntity();
                b.setBatchNo(this.seqNoGenerator.next("RB-" + SeqNoGenerator.todaySegment(), 4));
                b.setName(name.trim());
                b.setSupplierCode(this.blankToNull(supplierCode));
                b.setSourceType(src);
                b.setItemCount(0);
                b.setScannedCount(0);
                b.setConfirmedCount(0);
                b.setStatus("草稿");
                b.setCreator(this.operator());
                b.setRemark(this.blankToNull(remark));
                b.setCreatedAt(LocalDateTime.now());
                this.batchMapper.insert(b);
                return this.batchRow(this.requireBatch(b.getBatchNo()));
            }
        } else {
            throw BusinessException.badRequest("批次名称不能为空");
        }
    }

    public PageResult<Map<String, Object>> list(long page, long size, String status, String keyword) {
        Page<RfidBatchEntity> p = new Page(page, size);
        this.batchMapper
            .selectPage(
                p,
                (((Wrappers.<RfidBatchEntity>lambdaQuery()
                                .eq(status != null && !status.isBlank(), RfidBatchEntity::getStatus, this.blankToNull(status)))
                            .and(
                                keyword != null && !keyword.isBlank(),
                                w -> ((((w.like(
                                                        RfidBatchEntity::getBatchNo, keyword.trim()
                                                    ))
                                                    .or())
                                                .like(RfidBatchEntity::getName, keyword.trim()))
                                            .or())
                                        .like(RfidBatchEntity::getSupplierCode, keyword.trim())
                            ))
                        .orderByDesc(RfidBatchEntity::getCreatedAt))
                    .orderByDesc(RfidBatchEntity::getBatchNo)
            );
        return PageResult.of(p, this::batchRow);
    }

    public Map<String, Object> detail(String batchNo) {
        RfidBatchEntity b = this.requireBatch(batchNo);
        Map<String, Object> out = this.batchRow(b);
        out.put(
            "items",
            this.itemMapper
                .selectList(
                    (Wrappers.<RfidBatchItemEntity>lambdaQuery().eq(RfidBatchItemEntity::getBatchNo, batchNo)).orderByAsc(RfidBatchItemEntity::getId)
                )
                .stream()
                .map(this::itemRow)
                .toList()
        );
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", this.scanMapper.selectCount(Wrappers.<RfidScanRecordEntity>lambdaQuery().eq(RfidScanRecordEntity::getBatchNo, batchNo)));
        stats.put("scanned", b.getScannedCount());
        stats.put("confirmed", b.getConfirmedCount());
        stats.put(
            "abnormal",
            this.scanMapper
                .selectCount(
                    (Wrappers.<RfidScanRecordEntity>lambdaQuery().eq(RfidScanRecordEntity::getBatchNo, batchNo))
                        .eq(RfidScanRecordEntity::getScanStatus, "异常")
                )
        );
        out.put("stats", stats);
        return out;
    }

    @Transactional
    public Map<String, Object> addItem(String batchNo, String materialCode, Integer expectedQty) {
        RfidBatchEntity b = this.requireBatch(batchNo);
        this.requireItemEditable(b);
        String code = this.blankToNull(materialCode);
        if (code == null) {
            throw BusinessException.badRequest("物料编码不能为空");
        } else if (expectedQty != null && expectedQty >= 1) {
            String matName = this.materialQueryMapper.selectNameByCode(code);
            if (matName == null) {
                throw BusinessException.badRequest("物料不存在：" + code);
            } else if (this.itemExists(batchNo, code)) {
                throw BusinessException.conflict("该批次已存在物料 " + code + " 的明细");
            } else {
                RfidBatchItemEntity item = new RfidBatchItemEntity();
                item.setBatchNo(batchNo);
                item.setMaterialCode(code);
                item.setExpectedQty(expectedQty);
                item.setReceivedQty(0);
                this.itemMapper.insert(item);
                this.batchMapper.incrCounter("item_count", batchNo);
                return this.itemRow((RfidBatchItemEntity)this.itemMapper.selectById(item.getId()));
            }
        } else {
            throw BusinessException.badRequest("预期数量必须为正整数");
        }
    }

    @Transactional
    public Map<String, Object> updateItem(String batchNo, Long itemId, Integer expectedQty) {
        RfidBatchEntity b = this.requireBatch(batchNo);
        this.requireItemEditable(b);
        RfidBatchItemEntity item = this.requireItem(batchNo, itemId);
        if (expectedQty == null || expectedQty < 1) {
            throw BusinessException.badRequest("预期数量必须为正整数");
        } else if (expectedQty < item.getReceivedQty()) {
            throw BusinessException.conflict("预期数量不可小于已收数量（已收 " + item.getReceivedQty() + "）");
        } else {
            RfidBatchItemEntity upd = new RfidBatchItemEntity();
            upd.setExpectedQty(expectedQty);
            this.itemMapper.update(upd, Wrappers.<RfidBatchItemEntity>lambdaUpdate().eq(RfidBatchItemEntity::getId, item.getId()));
            return this.itemRow((RfidBatchItemEntity)this.itemMapper.selectById(item.getId()));
        }
    }

    @Transactional
    public void deleteItem(String batchNo, Long itemId) {
        RfidBatchEntity b = this.requireBatch(batchNo);
        if (!"草稿".equals(b.getStatus())) {
            throw BusinessException.conflict("批次当前状态「" + b.getStatus() + "」，仅草稿状态允许删除明细");
        } else {
            RfidBatchItemEntity item = this.requireItem(batchNo, itemId);
            if (item.getReceivedQty() != null && item.getReceivedQty() > 0) {
                throw BusinessException.conflict("明细「" + item.getMaterialCode() + "」已有收货数量，不允许删除");
            } else {
                this.itemMapper.deleteById(item.getId());
                this.batchMapper.decrCounter("item_count", batchNo);
            }
        }
    }

    @Transactional
    public Map<String, Object> importItems(String batchNo, MultipartFile file) throws IOException {
        RfidBatchEntity b = this.requireBatch(batchNo);
        this.requireItemEditable(b);
        if (file != null && !file.isEmpty()) {
            List<List<String>> rows = CsvSupport.parse(new String(file.getBytes(), StandardCharsets.UTF_8));
            if (rows.isEmpty()) {
                throw BusinessException.badRequest("CSV 文件为空");
            } else {
                boolean hasHeader = !rows.get(0).isEmpty() && "物料编码".equals(rows.get(0).get(0).trim());
                List<List<String>> dataRows = rows.subList(hasHeader ? 1 : 0, rows.size());
                int success = 0;
                List<Map<String, Object>> errors = new ArrayList<>();
                Set<String> fileCodes = new HashSet<>();

                for (int i = 0; i < dataRows.size(); i++) {
                    int rowNo = (hasHeader ? 1 : 0) + i + 1;
                    List<String> cells = dataRows.get(i);

                    try {
                        String code = this.cellAt(cells, 0).trim();
                        String qtyText = this.cellAt(cells, 1).trim();
                        if (code.isEmpty()) {
                            throw BusinessException.badRequest("物料编码不能为空");
                        }

                        if (this.materialQueryMapper.selectNameByCode(code) == null) {
                            throw BusinessException.badRequest("物料不存在：" + code);
                        }

                        int qty;
                        try {
                            qty = Integer.parseInt(qtyText);
                        } catch (NumberFormatException var17) {
                            throw BusinessException.badRequest("预期数量必须为正整数：" + qtyText);
                        }

                        if (qty < 1) {
                            throw BusinessException.badRequest("预期数量必须为正整数：" + qtyText);
                        }

                        if (this.itemExists(batchNo, code)) {
                            throw BusinessException.conflict("该批次已存在物料 " + code + " 的明细");
                        }

                        if (!fileCodes.add(code)) {
                            throw BusinessException.conflict("文件内物料编码重复：" + code);
                        }

                        RfidBatchItemEntity item = new RfidBatchItemEntity();
                        item.setBatchNo(batchNo);
                        item.setMaterialCode(code);
                        item.setExpectedQty(qty);
                        item.setReceivedQty(0);
                        this.itemMapper.insert(item);
                        success++;
                    } catch (BusinessException var18) {
                        fileCodes.remove(this.cellAt(cells, 0).trim());
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("row", rowNo);
                        err.put("reason", var18.getMessage());
                        errors.add(err);
                    }
                }

                for (int i = 0; i < success; i++) {
                    this.batchMapper.incrCounter("item_count", batchNo);
                }

                Map<String, Object> out = new LinkedHashMap<>();
                out.put("total", dataRows.size());
                out.put("success", success);
                out.put("failed", errors.size());
                out.put("errors", errors);
                return out;
            }
        } else {
            throw BusinessException.badRequest("请上传要导入的 CSV 文件");
        }
    }

    @Transactional
    public Map<String, Object> start(String batchNo) {
        this.requireBatch(batchNo);
        Long items = this.itemMapper.selectCount(Wrappers.<RfidBatchItemEntity>lambdaQuery().eq(RfidBatchItemEntity::getBatchNo, batchNo));
        if (items == null || items == 0L) {
            throw BusinessException.badRequest("请先添加批次明细");
        } else if (this.batchMapper.casStatus(batchNo, "草稿", "扫描中") == 0) {
            throw BusinessException.conflict("批次不是草稿状态，无法开始扫描");
        } else {
            return this.batchRow(this.requireBatch(batchNo));
        }
    }

    public Map<String, Object> scan(String batchNo, String epc, String sn, String materialCode, String exceptionNote) {
        this.requireBatch(batchNo);
        String normEpc = this.blankToNull(epc);
        if (normEpc == null) {
            throw BusinessException.badRequest("EPC 不能为空");
        } else {
            RfidScanRecordEntity existing = this.scanByEpc(batchNo, normEpc);
            if (existing != null) {
                return this.duplicateResult(existing);
            } else {
                try {
                    return (Map<String, Object>)this.scanTx
                        .execute(tx -> this.doScan(batchNo, normEpc, this.normalizeSn(sn), this.blankToNull(materialCode), exceptionNote));
                } catch (DuplicateKeyException var10) {
                    RfidScanRecordEntity hit = this.scanByEpc(batchNo, normEpc);
                    if (hit == null) {
                        throw var10;
                    } else {
                        return this.duplicateResult(hit);
                    }
                }
            }
        }
    }

    @Transactional
    public Map<String, Object> confirm(
        String batchNo, String namePrefix, String categoryCode, String modelCode, String ownerType, String regionCode, String remark
    ) {
        RfidBatchEntity batch = this.requireBatch(batchNo);
        if (this.batchMapper.casStatus(batchNo, "扫描中", "已确认") == 0) {
            throw BusinessException.conflict("批次不是扫描中状态，无法确认入库（当前「" + batch.getStatus() + "」）");
        } else {
            List<RfidScanRecordEntity> records = this.scanMapper
                .selectList(
                    (((Wrappers.<RfidScanRecordEntity>lambdaQuery()
                                    .eq(RfidScanRecordEntity::getBatchNo, batchNo))
                                .eq(RfidScanRecordEntity::getScanStatus, "已扫描"))
                            .isNull(RfidScanRecordEntity::getAssetNo))
                        .orderByAsc(RfidScanRecordEntity::getId)
                );
            int generated = 0;
            int seq = 0;

            for (RfidScanRecordEntity rec : records) {
                seq++;
                String epc = rec.getEpc();
                String name = this.blankToNull(namePrefix) != null
                    ? namePrefix.trim() + epc.substring(Math.max(0, epc.length() - 6))
                    : batch.getName() + "-" + seq;
                String sn = this.blankToNull(rec.getSn()) != null ? rec.getSn().trim().toUpperCase() : this.truncate("NB-" + epc, 100);
                Map<String, Object> attrs = new LinkedHashMap<>();
                attrs.put("epc", epc);
                attrs.put("rfidBatch", batchNo);
                attrs.put("source", "RFID批次验收");
                if (this.blankToNull(remark) != null) {
                    attrs.put("remark", remark.trim());
                }

                // 事务级咨询锁串行化同 SN 的“查重-建资产”窗口，消除并发确认的 check-then-act 竞态（无 DDL）
                this.assetQueryMapper.lockSn(sn);
                if (this.assetQueryMapper.countBySn(sn) > 0L) {
                    String reason = "SN 已存在：" + sn + "，跳过建资产";
                    this.markException(rec, "重复SN", reason);
                } else {
                    String assetNo = this.assetOpsPort
                        .createAssetForRfid(
                            name, sn, this.blankToNull(categoryCode), this.blankToNull(modelCode), ownerType, this.blankToNull(regionCode), attrs, batchNo
                        );
                    this.scanMapper
                        .update(
                            null,
                            ((Wrappers.<RfidScanRecordEntity>lambdaUpdate().eq(RfidScanRecordEntity::getId, rec.getId()))
                                    .set(RfidScanRecordEntity::getAssetNo, assetNo))
                                .set(RfidScanRecordEntity::getScanStatus, "已入库")
                        );
                    this.batchMapper.incrCounter("confirmed_count", batchNo);
                    generated++;
                }
            }

            List<RfidScanRecordEntity> abnormal = this.scanMapper
                .selectList(
                    ((Wrappers.<RfidScanRecordEntity>lambdaQuery().eq(RfidScanRecordEntity::getBatchNo, batchNo))
                            .eq(RfidScanRecordEntity::getScanStatus, "异常"))
                        .orderByAsc(RfidScanRecordEntity::getId)
                );
            List<Map<String, Object>> exceptions = new ArrayList<>();

            for (RfidScanRecordEntity r : abnormal) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("epc", r.getEpc());
                e.put("sn", r.getSn());
                e.put("reason", (r.getExceptionType() == null ? "" : r.getExceptionType() + "：") + (r.getExceptionNote() == null ? "" : r.getExceptionNote()));
                exceptions.add(e);
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("generated", generated);
            out.put("skipped", abnormal.size());
            out.put("exceptions", exceptions);
            return out;
        }
    }

    @Transactional
    public Map<String, Object> cancel(String batchNo) {
        RfidBatchEntity b = this.requireBatch(batchNo);
        if (!"草稿".equals(b.getStatus()) && !"扫描中".equals(b.getStatus())) {
            throw BusinessException.conflict("批次已「" + b.getStatus() + "」，不允许取消");
        } else if (this.batchMapper.casStatus(batchNo, b.getStatus(), "已取消") == 0) {
            throw BusinessException.conflict("批次状态已被并发操作变更，请刷新后重试");
        } else {
            return this.batchRow(this.requireBatch(batchNo));
        }
    }

    public PageResult<Map<String, Object>> scans(String batchNo, long page, long size, String scanStatus) {
        this.requireBatch(batchNo);
        Page<RfidScanRecordEntity> p = new Page(page, size);
        this.scanMapper
            .selectPage(
                p,
                (((Wrappers.<RfidScanRecordEntity>lambdaQuery().eq(RfidScanRecordEntity::getBatchNo, batchNo))
                            .eq(scanStatus != null && !scanStatus.isBlank(), RfidScanRecordEntity::getScanStatus, this.blankToNull(scanStatus)))
                        .orderByDesc(RfidScanRecordEntity::getScanTime))
                    .orderByDesc(RfidScanRecordEntity::getId)
            );
        return PageResult.of(p, this::scanRow);
    }

    private Map<String, Object> doScan(String batchNo, String epc, String sn, String materialCode, String exceptionNote) {
        if (this.batchMapper.casStatus(batchNo, "扫描中", "扫描中") == 0) {
            throw BusinessException.conflict("批次不是扫描中状态，无法扫码");
        } else {
            String scanStatus = "已扫描";
            String exceptionType = null;
            String note = this.blankToNull(exceptionNote);
            RfidBatchItemEntity matched = null;
            if (materialCode == null) {
                scanStatus = "异常";
                exceptionType = "未匹配";
                note = this.joinNote(note, "扫描未指定物料编码或不在批次明细中");
            } else {
                matched = (RfidBatchItemEntity)this.itemMapper
                    .selectOne(
                        (Wrappers.<RfidBatchItemEntity>lambdaQuery().eq(RfidBatchItemEntity::getBatchNo, batchNo))
                            .eq(RfidBatchItemEntity::getMaterialCode, materialCode)
                    );
                if (matched == null) {
                    scanStatus = "异常";
                    exceptionType = "未匹配";
                    note = this.joinNote(note, "物料 " + materialCode + " 不在该批次明细中");
                } else if (sn != null && this.assetQueryMapper.countBySn(sn) > 0L) {
                    // SN 重复检查须在 incrReceived 占配额之前：改判异常时无需回减 received_qty，避免配额虚占
                    scanStatus = "异常";
                    exceptionType = "重复SN";
                    note = this.joinNote(note, "SN 已存在于资产库：" + sn);
                } else if (this.itemMapper.incrReceived(matched.getId()) == 0) {
                    scanStatus = "异常";
                    exceptionType = "超量";
                    note = this.joinNote(note, "物料 " + materialCode + " 已收满预期 " + matched.getExpectedQty() + " 件");
                }
            }

            RfidScanRecordEntity rec = new RfidScanRecordEntity();
            rec.setBatchNo(batchNo);
            rec.setEpc(epc);
            rec.setAssetNo(null);
            rec.setMaterialCode(materialCode);
            rec.setSn(sn);
            rec.setScanStatus(scanStatus);
            rec.setExceptionType(exceptionType);
            rec.setExceptionNote(note);
            rec.setOperator(this.operator());
            rec.setScanTime(LocalDateTime.now());
            this.scanMapper.insert(rec);
            this.batchMapper.incrCounter("scanned_count", batchNo);
            Map<String, Object> out = this.scanRow(rec);
            out.put("duplicate", false);
            return out;
        }
    }

    private RfidBatchEntity requireBatch(String batchNo) {
        RfidBatchEntity b = (RfidBatchEntity)this.batchMapper.selectById(batchNo);
        if (b == null) {
            throw BusinessException.notFound("批次不存在：" + batchNo);
        } else {
            return b;
        }
    }

    private void requireItemEditable(RfidBatchEntity b) {
        if (!"草稿".equals(b.getStatus()) && !"扫描中".equals(b.getStatus())) {
            throw BusinessException.conflict("批次当前状态「" + b.getStatus() + "」，不允许维护明细");
        }
    }

    private RfidBatchItemEntity requireItem(String batchNo, Long itemId) {
        RfidBatchItemEntity item = itemId == null ? null : (RfidBatchItemEntity)this.itemMapper.selectById(itemId);
        if (item != null && batchNo.equals(item.getBatchNo())) {
            return item;
        } else {
            throw BusinessException.notFound("批次明细不存在：" + itemId);
        }
    }

    private boolean itemExists(String batchNo, String materialCode) {
        return this.itemMapper
                .selectCount(
                    (Wrappers.<RfidBatchItemEntity>lambdaQuery().eq(RfidBatchItemEntity::getBatchNo, batchNo))
                        .eq(RfidBatchItemEntity::getMaterialCode, materialCode)
                )
            > 0L;
    }

    private RfidScanRecordEntity scanByEpc(String batchNo, String epc) {
        return (RfidScanRecordEntity)this.scanMapper
            .selectOne(
                (Wrappers.<RfidScanRecordEntity>lambdaQuery().eq(RfidScanRecordEntity::getBatchNo, batchNo)).eq(RfidScanRecordEntity::getEpc, epc)
            );
    }

    private void markException(RfidScanRecordEntity rec, String type, String reason) {
        this.scanMapper
            .update(
                null,
                (((Wrappers.<RfidScanRecordEntity>lambdaUpdate().eq(RfidScanRecordEntity::getId, rec.getId()))
                            .set(RfidScanRecordEntity::getScanStatus, "异常"))
                        .set(RfidScanRecordEntity::getExceptionType, type))
                    .set(RfidScanRecordEntity::getExceptionNote, reason)
            );
    }

    private Map<String, Object> duplicateResult(RfidScanRecordEntity existing) {
        Map<String, Object> out = this.scanRow(existing);
        out.put("duplicate", true);
        return out;
    }

    private String normalizeSn(String sn) {
        return sn != null && !sn.isBlank() ? sn.trim().toUpperCase() : null;
    }

    private String operator() {
        LoginUser u = CurrentUser.get();
        return u == null ? null : u.username();
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String joinNote(String base, String extra) {
        return base == null ? extra : base + "；" + extra;
    }

    private String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private String cellAt(List<String> cells, int idx) {
        return idx >= 0 && idx < cells.size() ? cells.get(idx) : "";
    }

    private Map<String, Object> batchRow(RfidBatchEntity b) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("batchNo", b.getBatchNo());
        row.put("name", b.getName());
        row.put("supplierCode", b.getSupplierCode());
        row.put("sourceType", b.getSourceType());
        row.put("itemCount", b.getItemCount());
        row.put("scannedCount", b.getScannedCount());
        row.put("confirmedCount", b.getConfirmedCount());
        row.put("status", b.getStatus());
        row.put("creator", b.getCreator());
        row.put("confirmedAt", b.getConfirmedAt());
        row.put("remark", b.getRemark());
        row.put("createdAt", b.getCreatedAt());
        return row;
    }

    private Map<String, Object> itemRow(RfidBatchItemEntity i) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", i.getId());
        row.put("batchNo", i.getBatchNo());
        row.put("materialCode", i.getMaterialCode());
        row.put("expectedQty", i.getExpectedQty());
        row.put("receivedQty", i.getReceivedQty());
        return row;
    }

    private Map<String, Object> scanRow(RfidScanRecordEntity r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.getId());
        row.put("batchNo", r.getBatchNo());
        row.put("epc", r.getEpc());
        row.put("assetNo", r.getAssetNo());
        row.put("materialCode", r.getMaterialCode());
        row.put("sn", r.getSn());
        row.put("scanStatus", r.getScanStatus());
        row.put("exceptionType", r.getExceptionType());
        row.put("exceptionNote", r.getExceptionNote());
        row.put("operator", r.getOperator());
        row.put("scanTime", r.getScanTime());
        return row;
    }
}
