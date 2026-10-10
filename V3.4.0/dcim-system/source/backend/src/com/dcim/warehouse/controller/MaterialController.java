package com.dcim.warehouse.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.common.csv.CsvSupport;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import com.dcim.system.entity.DictEntity;
import com.dcim.system.mapper.DictMapper;
import com.dcim.warehouse.entity.MaterialEntity;
import com.dcim.warehouse.entity.StockLogEntity;
import com.dcim.warehouse.entity.WarehouseEntity;
import com.dcim.warehouse.entity.WarehouseLayerEntity;
import com.dcim.warehouse.entity.WarehouseShelfEntity;
import com.dcim.warehouse.mapper.MaterialMapper;
import com.dcim.warehouse.mapper.StockLogMapper;
import com.dcim.warehouse.mapper.WarehouseLayerMapper;
import com.dcim.warehouse.mapper.WarehouseMapper;
import com.dcim.warehouse.mapper.WarehouseShelfMapper;
import com.dcim.warehouse.service.LowStockService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/materials"})
public class MaterialController {
    private final MaterialMapper materialMapper;
    private final StockLogMapper stockLogMapper;
    private final DictMapper dictMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseShelfMapper shelfMapper;
    private final WarehouseLayerMapper layerMapper;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate rowTx;
    private final LowStockService lowStockService;
    private static final List<String> CSV_HEADERS = List.of(
        "物料编码", "物料名称", "分类", "类型", "品牌", "型号", "PN", "规格", "单位", "参考单价", "安全库存", "最高库存", "存放位置", "供应商编码", "仓库编号", "货架编号", "层位编号"
    );

    @GetMapping
    @RequirePerm(
        module = "materials"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String category
    ) {
        LambdaQueryWrapper<MaterialEntity> qw = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(
                w -> ((((((w.like(
                                                MaterialEntity::getMaterialCode, kw
                                            ))
                                            .or())
                                        .like(MaterialEntity::getName, kw))
                                    .or())
                                .like(MaterialEntity::getBrand, kw))
                            .or())
                        .like(MaterialEntity::getModel, kw)
            );
        }

        if (category != null && !category.isBlank()) {
            qw.eq(MaterialEntity::getCategory, category.trim());
        }

        (qw.orderByDesc(MaterialEntity::getCreatedAt)).orderByAsc(MaterialEntity::getMaterialCode);
        Page<MaterialEntity> p = new Page(page, size);
        this.materialMapper.selectPage(p, qw);
        List<Map<String, Object>> rows = p.getRecords().stream().map(this::toRow).toList();
        this.enrichWarehouseStocks(rows);
        return ApiResult.ok(new PageResult<>(p.getTotal(), rows));
    }

    @GetMapping({"/meta"})
    @RequirePerm(
        module = "materials"
    )
    public ApiResult<Map<String, Object>> meta() {
        List<DictEntity> dicts = this.dictMapper
            .selectList(
                ((Wrappers.<DictEntity>lambdaQuery().in(DictEntity::getDictType, List.of("unit", "material_cat")))
                        .eq(DictEntity::getStatus, 1))
                    .orderByAsc(DictEntity::getSort)
            );
        List<String> units = new ArrayList<>();
        List<String> categories = new ArrayList<>();

        for (DictEntity d : dicts) {
            if ("unit".equals(d.getDictType())) {
                units.add(d.getDictKey());
            } else if ("material_cat".equals(d.getDictType())) {
                categories.add(d.getDictKey());
            }
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("units", units);
        meta.put("categories", categories);
        return ApiResult.ok(meta);
    }

    @PostMapping
    @RequirePerm(
        module = "materials",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "materials",
        objType = "material",
        objIdSpEL = "#req.materialCode"
    )
    @Transactional
    public ApiResult<Map<String, Object>> create(@RequestBody @Valid MaterialController.CreateMaterialReq req) {
        String code = req.getMaterialCode().trim();
        if (this.selectByCode(code) != null) {
            throw BusinessException.conflict("物料编码已存在：" + code);
        } else {
            this.validateUnitAndCategory(req.getUnit(), req.getCategory());
            MaterialController.LocationRef loc = this.resolveLocation(req.getWarehouseCode(), req.getShelfCode(), req.getLayerCode());
            MaterialEntity e = new MaterialEntity();
            e.setMaterialCode(code);
            e.setName(req.getName().trim());
            e.setCategory(this.blankToNull(req.getCategory()));
            e.setType(this.blankToNull(req.getType()));
            e.setBrand(this.blankToNull(req.getBrand()));
            e.setModel(this.blankToNull(req.getModel()));
            e.setPn(this.blankToNull(req.getPn()));
            e.setSpec(this.blankToNull(req.getSpec()));
            e.setUnit(req.getUnit().trim());
            e.setStock(0);
            e.setSafetyStock(req.getSafetyStock() == null ? 0 : req.getSafetyStock());
            e.setMaxStock(req.getMaxStock() == null ? 0 : req.getMaxStock());
            e.setTotalIn(0);
            e.setTotalOut(0);
            e.setPrice(req.getPrice());
            e.setLocation(this.blankToNull(req.getLocation()));
            e.setWarehouseCode(loc.warehouseCode());
            e.setShelfCode(loc.shelfCode());
            e.setLayerCode(loc.layerCode());
            e.setSupplierCode(this.blankToNull(req.getSupplierCode()));
            e.setRemark(this.blankToNull(req.getRemark()));
            e.setVersion(1);
            e.setCreatedAt(LocalDateTime.now());
            e.setUpdatedAt(LocalDateTime.now());
            this.materialMapper.insert(e);
            Map<String, Object> out = this.toRow(e);
            this.enrichWarehouseStocks(List.of(out));
            return ApiResult.ok(out);
        }
    }

    @PutMapping({"/{materialCode}"})
    @RequirePerm(
        module = "materials",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "materials",
        objType = "material",
        objIdSpEL = "#materialCode"
    )
    @Transactional
    public ApiResult<Map<String, Object>> update(@PathVariable String materialCode, @RequestBody @Valid MaterialController.UpdateMaterialReq req) {
        if (req.getVersion() == null) {
            throw BusinessException.badRequest("请求体缺少 version（乐观锁校验需要）");
        } else {
            MaterialEntity cur = this.requireMaterial(materialCode);
            this.assertStockUntouched("stock", req.getStock(), cur.getStock());
            this.assertStockUntouched("totalIn", req.getTotalIn(), cur.getTotalIn());
            this.assertStockUntouched("totalOut", req.getTotalOut(), cur.getTotalOut());
            if (req.getName() != null && req.getName().isBlank()) {
                throw BusinessException.badRequest("物料名称不能为空");
            } else {
                if (req.getUnit() != null) {
                    if (req.getUnit().isBlank()) {
                        throw BusinessException.badRequest("计量单位不能为空");
                    }

                    if (this.dictCount("unit", req.getUnit().trim()) == 0L) {
                        throw BusinessException.badRequest("计量单位不在单位字典中：" + req.getUnit().trim());
                    }
                }

                if (req.getCategory() != null && !req.getCategory().isBlank() && this.dictCount("material_cat", req.getCategory().trim()) == 0L) {
                    throw BusinessException.badRequest("物料分类不在分类字典中：" + req.getCategory().trim());
                } else {
                    MaterialEntity upd = new MaterialEntity();
                    upd.setVersion(cur.getVersion() + 1);
                    upd.setUpdatedAt(LocalDateTime.now());
                    if (req.getName() != null) {
                        upd.setName(req.getName().trim());
                    }

                    if (req.getCategory() != null) {
                        upd.setCategory(this.blankToNull(req.getCategory()));
                    }

                    upd.setType(req.getType());
                    upd.setBrand(req.getBrand());
                    upd.setModel(req.getModel());
                    upd.setPn(req.getPn());
                    upd.setSpec(req.getSpec());
                    if (req.getUnit() != null) {
                        upd.setUnit(req.getUnit().trim());
                    }

                    upd.setPrice(req.getPrice());
                    if (req.getSafetyStock() != null) {
                        upd.setSafetyStock(req.getSafetyStock());
                    }

                    if (req.getMaxStock() != null) {
                        upd.setMaxStock(req.getMaxStock());
                    }

                    upd.setLocation(req.getLocation());
                    upd.setSupplierCode(req.getSupplierCode());
                    upd.setRemark(this.blankToNull(req.getRemark()));
                    MaterialController.LocationRef loc = this.resolveLocation(req.getWarehouseCode(), req.getShelfCode(), req.getLayerCode());
                    upd.setWarehouseCode(loc.warehouseCode());
                    upd.setShelfCode(loc.shelfCode());
                    upd.setLayerCode(loc.layerCode());
                    int rows = this.materialMapper
                        .update(
                            upd,
                            (Wrappers.<MaterialEntity>lambdaUpdate().eq(MaterialEntity::getMaterialCode, cur.getMaterialCode()))
                                .eq(MaterialEntity::getVersion, req.getVersion())
                        );
                    if (rows == 0) {
                        throw BusinessException.conflict("数据已被他人修改，请刷新后重试");
                    } else {
                        // 修复 A4：安全库存调高到高于当前库存时补一次低库存评估——
                        // 否则该物料在下次出入库前，notify/ticket 联动（仅由 stock.movement 事件触发）永不提醒。
                        // onLowStock 在事务内仅注册 afterCommit 钩子，提交后异步执行，不会读到未提交数据
                        Integer newSafety = req.getSafetyStock();
                        int curStock = cur.getStock() == null ? 0 : cur.getStock();
                        if (newSafety != null && newSafety > curStock && !newSafety.equals(cur.getSafetyStock())) {
                            this.lowStockService
                                .onLowStock(cur.getMaterialCode(), cur.getName(), curStock, newSafety, cur.getUnit(), null);
                        }

                        Map<String, Object> out = this.toRow(this.requireMaterial(materialCode));
                        this.enrichWarehouseStocks(List.of(out));
                        return ApiResult.ok(out);
                    }
                }
            }
        }
    }

    @DeleteMapping({"/{materialCode}"})
    @RequirePerm(
        module = "materials",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "materials",
        objType = "material",
        objIdSpEL = "#materialCode"
    )
    @Transactional
    public ApiResult<Void> delete(@PathVariable String materialCode) {
        MaterialEntity cur = this.requireMaterial(materialCode);
        int stock = cur.getStock() == null ? 0 : cur.getStock();
        if (stock != 0) {
            throw BusinessException.conflict("物料库存不为 0（当前 " + stock + (cur.getUnit() == null ? "" : cur.getUnit()) + "），不允许删除");
        } else {
            Long logs = this.stockLogMapper.selectCount(Wrappers.<StockLogEntity>lambdaQuery().eq(StockLogEntity::getMaterialCode, cur.getMaterialCode()));
            if (logs != null && logs > 0L) {
                throw BusinessException.conflict("物料存在 " + logs + " 条出入库流水，不允许删除");
            } else {
                this.jdbc.update("DELETE FROM stock_level WHERE material_code = ?", new Object[]{cur.getMaterialCode()});
                this.materialMapper.deleteById(cur.getMaterialCode());
                return ApiResult.ok();
            }
        }
    }

    @GetMapping({"/template"})
    @RequirePerm(
        module = "materials"
    )
    public void template(HttpServletResponse response) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff').append(CsvSupport.line(CSV_HEADERS)).append('\n');
        csv.append(
                CsvSupport.line(
                    List.of("MAT-EXAMPLE-0001", "示例网线", "线缆", "", "示例品牌", "CAT6", "PN-001", "六类非屏蔽", "根", "2.50", "100", "1000", "A库-01", "", "WH-001", "", "")
                )
            )
            .append('\n');
        this.writeCsv(response, "物料导入模板.csv", csv.toString());
    }

    @PostMapping(
        value = {"/import"},
        consumes = {"multipart/form-data"}
    )
    @RequirePerm(
        module = "materials",
        write = true,
        action = "import"
    )
    @Audit(
        action = "IMPORT",
        module = "materials",
        objType = "material"
    )
    public ApiResult<Map<String, Object>> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        if (file != null && !file.isEmpty()) {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<List<String>> rows = CsvSupport.parse(text);
            if (rows.isEmpty()) {
                throw BusinessException.badRequest("CSV 文件为空");
            } else {
                List<String> header = rows.get(0);
                String firstCell = header.isEmpty() ? "" : header.get(0).trim();
                boolean hasHeader = "物料编码".equals(firstCell) || "物料名称".equals(firstCell);
                Map<String, Integer> colIndex = new LinkedHashMap<>();
                if (hasHeader) {
                    for (int i = 0; i < header.size(); i++) {
                        colIndex.put(header.get(i).trim(), i);
                    }
                }

                List<List<String>> dataRows = rows.subList(hasHeader ? 1 : 0, rows.size());
                Set<String> fileCodes = new HashSet<>();
                int success = 0;
                List<Map<String, Object>> errors = new ArrayList<>();

                for (int i = 0; i < dataRows.size(); i++) {
                    int rowNo = (hasHeader ? 1 : 0) + i + 1;

                    try {
                        this.importMaterialRow(dataRows.get(i), colIndex, fileCodes);
                        success++;
                    } catch (BusinessException var16) {
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("row", rowNo);
                        err.put("reason", var16.getMessage());
                        errors.add(err);
                    } catch (DataIntegrityViolationException var17) {
                        Map<String, Object> errx = new LinkedHashMap<>();
                        errx.put("row", rowNo);
                        errx.put("reason", "数据写入冲突（可能已被并发导入）");
                        errors.add(errx);
                    }
                }

                Map<String, Object> out = new LinkedHashMap<>();
                out.put("total", dataRows.size());
                out.put("success", success);
                out.put("failed", errors.size());
                out.put("errors", errors);
                return ApiResult.ok(out);
            }
        } else {
            throw BusinessException.badRequest("请上传要导入的 CSV 文件");
        }
    }

    private void importMaterialRow(List<String> cells, Map<String, Integer> colIndex, Set<String> fileCodes) {
        int[] ix = new int[]{
            col(colIndex, "物料编码", 0),
            col(colIndex, "物料名称", 1),
            col(colIndex, "分类", 2),
            col(colIndex, "类型", 3),
            col(colIndex, "品牌", 4),
            col(colIndex, "型号", 5),
            col(colIndex, "PN", 6),
            col(colIndex, "规格", 7),
            col(colIndex, "单位", 8),
            col(colIndex, "参考单价", 9),
            col(colIndex, "安全库存", 10),
            col(colIndex, "最高库存", 11),
            col(colIndex, "存放位置", 12),
            col(colIndex, "供应商编码", 13),
            col(colIndex, "仓库编号", 14),
            col(colIndex, "货架编号", 15),
            col(colIndex, "层位编号", 16)
        };
        String code = this.blankToNull(cellAt(cells, ix[0]));
        String name = this.blankToNull(cellAt(cells, ix[1]));
        String category = this.blankToNull(cellAt(cells, ix[2]));
        String type = this.blankToNull(cellAt(cells, ix[3]));
        String brand = this.blankToNull(cellAt(cells, ix[4]));
        String model = this.blankToNull(cellAt(cells, ix[5]));
        String pn = this.blankToNull(cellAt(cells, ix[6]));
        String spec = this.blankToNull(cellAt(cells, ix[7]));
        String unit = this.blankToNull(cellAt(cells, ix[8]));
        String price = this.blankToNull(cellAt(cells, ix[9]));
        String safetyStock = this.blankToNull(cellAt(cells, ix[10]));
        String maxStock = this.blankToNull(cellAt(cells, ix[11]));
        String location = this.blankToNull(cellAt(cells, ix[12]));
        String supplierCode = this.blankToNull(cellAt(cells, ix[13]));
        String warehouseCode = this.blankToNull(cellAt(cells, ix[14]));
        String shelfCode = this.blankToNull(cellAt(cells, ix[15]));
        String layerCode = this.blankToNull(cellAt(cells, ix[16]));
        if (code == null) {
            throw BusinessException.badRequest("物料编码不能为空");
        } else if (name == null) {
            throw BusinessException.badRequest("物料名称不能为空");
        } else if (unit == null) {
            throw BusinessException.badRequest("计量单位不能为空");
        } else {
            this.validateUnitAndCategory(unit, category);
            MaterialController.LocationRef loc = this.resolveLocation(warehouseCode, shelfCode, layerCode);
            if (this.selectByCode(code) != null) {
                throw BusinessException.conflict("物料编码已存在：" + code);
            } else if (fileCodes.contains(code)) {
                throw BusinessException.conflict("物料编码已存在（文件内重复）：" + code);
            } else {
                MaterialEntity e = new MaterialEntity();
                e.setMaterialCode(code);
                e.setName(name);
                e.setCategory(category);
                e.setType(type);
                e.setBrand(brand);
                e.setModel(model);
                e.setPn(pn);
                e.setSpec(spec);
                e.setUnit(unit);
                e.setStock(0);
                e.setSafetyStock(parseIntSafe(safetyStock, "安全库存", 0));
                e.setMaxStock(parseIntSafe(maxStock, "最高库存", 0));
                e.setTotalIn(0);
                e.setTotalOut(0);
                e.setPrice(parseDecimalSafe(price, "参考单价"));
                e.setLocation(location);
                e.setWarehouseCode(loc.warehouseCode());
                e.setShelfCode(loc.shelfCode());
                e.setLayerCode(loc.layerCode());
                e.setSupplierCode(supplierCode);
                e.setVersion(1);
                e.setCreatedAt(LocalDateTime.now());
                e.setUpdatedAt(LocalDateTime.now());

                try {
                    this.rowTx.executeWithoutResult(tx -> this.materialMapper.insert(e));
                } catch (DuplicateKeyException var25) {
                    throw BusinessException.conflict("物料编码已存在：" + code);
                }

                fileCodes.add(code);
            }
        }
    }

    private static int parseIntSafe(String s, String field, int fallback) {
        if (s != null && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException var4) {
                throw BusinessException.badRequest(field + " 必须是整数：" + s);
            }
        } else {
            return fallback;
        }
    }

    private static BigDecimal parseDecimalSafe(String s, String field) {
        if (s != null && !s.isBlank()) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException var3) {
                throw BusinessException.badRequest(field + " 必须是数字：" + s);
            }
        } else {
            return null;
        }
    }

    private static int col(Map<String, Integer> colIndex, String name, int fallback) {
        Integer i = colIndex.get(name);
        return i == null ? fallback : i;
    }

    private static String cellAt(List<String> cells, int idx) {
        return idx >= 0 && idx < cells.size() ? cells.get(idx) : null;
    }

    private void writeCsv(HttpServletResponse response, String fileName, String csv) throws IOException {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setStatus(200);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.getOutputStream().write(csv.getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }

    private MaterialController.LocationRef resolveLocation(String whRaw, String shelfRaw, String layerRaw) {
        String warehouseCode = this.blankToNull(whRaw);
        String shelfCode = this.blankToNull(shelfRaw);
        String layerCode = this.blankToNull(layerRaw);
        if (layerCode != null) {
            WarehouseLayerEntity layer = (WarehouseLayerEntity)this.layerMapper
                .selectOne(Wrappers.<WarehouseLayerEntity>lambdaQuery().eq(WarehouseLayerEntity::getLayerCode, layerCode));
            if (layer == null) {
                throw BusinessException.badRequest("层位不存在：" + layerCode);
            }

            if (shelfCode == null) {
                shelfCode = layer.getShelfCode();
            } else if (!shelfCode.equals(layer.getShelfCode())) {
                throw BusinessException.badRequest("层位 " + layerCode + " 不属于货架 " + shelfCode + "，请重新选择");
            }
        }

        if (shelfCode != null) {
            WarehouseShelfEntity shelf = (WarehouseShelfEntity)this.shelfMapper
                .selectOne(Wrappers.<WarehouseShelfEntity>lambdaQuery().eq(WarehouseShelfEntity::getShelfCode, shelfCode));
            if (shelf == null) {
                throw BusinessException.badRequest("货架不存在：" + shelfCode);
            }

            if (warehouseCode == null) {
                warehouseCode = shelf.getWarehouseCode();
            } else if (!warehouseCode.equals(shelf.getWarehouseCode())) {
                throw BusinessException.badRequest("货架 " + shelfCode + " 不属于仓库 " + warehouseCode + "，请重新选择");
            }
        }

        if (warehouseCode != null
            && this.warehouseMapper.selectOne(Wrappers.<WarehouseEntity>lambdaQuery().eq(WarehouseEntity::getWarehouseCode, warehouseCode)) == null) {
            throw BusinessException.badRequest("仓库不存在：" + warehouseCode);
        } else {
            return new MaterialController.LocationRef(warehouseCode, shelfCode, layerCode);
        }
    }

    private void enrichWarehouseStocks(List<Map<String, Object>> rows) {
        if (rows != null && !rows.isEmpty()) {
            List<String> codes = rows.stream().map(rx -> String.valueOf(rx.get("materialCode"))).toList();
            String in = String.join(",", Collections.nCopies(codes.size(), "?"));
            List<Map<String, Object>> levels = this.jdbc
                .queryForList(
                    "SELECT material_code AS \"materialCode\", warehouse_code AS \"warehouseCode\", qty AS \"qty\" FROM stock_level WHERE material_code IN ("
                        + in
                        + ") ORDER BY warehouse_code",
                    codes.toArray()
                );
            Map<String, Map<String, Object>> byMaterial = new LinkedHashMap<>();

            for (Map<String, Object> l : levels) {
                byMaterial.computeIfAbsent(String.valueOf(l.get("materialCode")), k -> new LinkedHashMap<>())
                    .put(String.valueOf(l.get("warehouseCode")), l.get("qty"));
            }

            for (Map<String, Object> r : rows) {
                r.put("warehouseStocks", byMaterial.getOrDefault(String.valueOf(r.get("materialCode")), new LinkedHashMap<>()));
            }
        }
    }

    private void validateUnitAndCategory(String unit, String category) {
        if (unit != null && !unit.isBlank()) {
            if (this.dictCount("unit", unit.trim()) == 0L) {
                throw BusinessException.badRequest("计量单位不在单位字典中：" + unit.trim());
            } else if (category != null && !category.isBlank() && this.dictCount("material_cat", category.trim()) == 0L) {
                throw BusinessException.badRequest("物料分类不在分类字典中：" + category.trim());
            }
        } else {
            throw BusinessException.badRequest("计量单位不能为空");
        }
    }

    private void assertStockUntouched(String field, Integer provided, Integer current) {
        if (provided != null && current != null && !provided.equals(current)) {
            throw BusinessException.badRequest(field + " 不允许直接修改（库存只能通过出入库流水变动），请调用 /api/stock-logs");
        }
    }

    private long dictCount(String dictType, String dictKey) {
        Long n = this.dictMapper
            .selectCount((Wrappers.<DictEntity>lambdaQuery().eq(DictEntity::getDictType, dictType)).eq(DictEntity::getDictKey, dictKey));
        return n == null ? 0L : n;
    }

    private MaterialEntity selectByCode(String code) {
        return (MaterialEntity)this.materialMapper.selectOne(Wrappers.<MaterialEntity>lambdaQuery().eq(MaterialEntity::getMaterialCode, code));
    }

    private MaterialEntity requireMaterial(String code) {
        MaterialEntity e = this.selectByCode(code);
        if (e == null) {
            throw BusinessException.notFound("物料不存在：" + code);
        } else {
            return e;
        }
    }

    private String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private Map<String, Object> toRow(MaterialEntity e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("materialCode", e.getMaterialCode());
        row.put("name", e.getName());
        row.put("category", e.getCategory());
        row.put("type", e.getType());
        row.put("brand", e.getBrand());
        row.put("model", e.getModel());
        row.put("pn", e.getPn());
        row.put("spec", e.getSpec());
        row.put("unit", e.getUnit());
        row.put("stock", e.getStock());
        row.put("safetyStock", e.getSafetyStock());
        row.put("maxStock", e.getMaxStock());
        row.put("totalIn", e.getTotalIn());
        row.put("totalOut", e.getTotalOut());
        row.put("price", e.getPrice());
        row.put("location", e.getLocation());
        row.put("warehouseCode", e.getWarehouseCode());
        row.put("shelfCode", e.getShelfCode());
        row.put("layerCode", e.getLayerCode());
        row.put("supplierCode", e.getSupplierCode());
        row.put("remark", e.getRemark());
        row.put("warehouseStocks", new LinkedHashMap());
        row.put("version", e.getVersion());
        row.put("createdAt", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
        row.put("updatedAt", e.getUpdatedAt() == null ? "" : e.getUpdatedAt().toString());
        return row;
    }

    
    public MaterialController(
        final MaterialMapper materialMapper,
        final StockLogMapper stockLogMapper,
        final DictMapper dictMapper,
        final WarehouseMapper warehouseMapper,
        final WarehouseShelfMapper shelfMapper,
        final WarehouseLayerMapper layerMapper,
        final JdbcTemplate jdbc,
        final TransactionTemplate rowTx,
        final LowStockService lowStockService
    ) {
        this.materialMapper = materialMapper;
        this.stockLogMapper = stockLogMapper;
        this.dictMapper = dictMapper;
        this.warehouseMapper = warehouseMapper;
        this.shelfMapper = shelfMapper;
        this.layerMapper = layerMapper;
        this.jdbc = jdbc;
        this.rowTx = rowTx;
        this.lowStockService = lowStockService;
    }

    public static class CreateMaterialReq {
        @NotBlank(
            message = "物料编码不能为空"
        )
        private String materialCode;
        @NotBlank(
            message = "物料名称不能为空"
        )
        private String name;
        private String category;
        private String type;
        private String brand;
        private String model;
        private String pn;
        private String spec;
        private String unit;
        private BigDecimal price;
        private Integer safetyStock;
        private Integer maxStock;
        private String location;
        private String warehouseCode;
        private String shelfCode;
        private String layerCode;
        private String supplierCode;
        private String remark;

        
        public String getMaterialCode() {
            return this.materialCode;
        }

        
        public String getName() {
            return this.name;
        }

        
        public String getCategory() {
            return this.category;
        }

        
        public String getType() {
            return this.type;
        }

        
        public String getBrand() {
            return this.brand;
        }

        
        public String getModel() {
            return this.model;
        }

        
        public String getPn() {
            return this.pn;
        }

        
        public String getSpec() {
            return this.spec;
        }

        
        public String getUnit() {
            return this.unit;
        }

        
        public BigDecimal getPrice() {
            return this.price;
        }

        
        public Integer getSafetyStock() {
            return this.safetyStock;
        }

        
        public Integer getMaxStock() {
            return this.maxStock;
        }

        
        public String getLocation() {
            return this.location;
        }

        
        public String getWarehouseCode() {
            return this.warehouseCode;
        }

        
        public String getShelfCode() {
            return this.shelfCode;
        }

        
        public String getLayerCode() {
            return this.layerCode;
        }

        
        public String getSupplierCode() {
            return this.supplierCode;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public void setMaterialCode(final String materialCode) {
            this.materialCode = materialCode;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setCategory(final String category) {
            this.category = category;
        }

        
        public void setType(final String type) {
            this.type = type;
        }

        
        public void setBrand(final String brand) {
            this.brand = brand;
        }

        
        public void setModel(final String model) {
            this.model = model;
        }

        
        public void setPn(final String pn) {
            this.pn = pn;
        }

        
        public void setSpec(final String spec) {
            this.spec = spec;
        }

        
        public void setUnit(final String unit) {
            this.unit = unit;
        }

        
        public void setPrice(final BigDecimal price) {
            this.price = price;
        }

        
        public void setSafetyStock(final Integer safetyStock) {
            this.safetyStock = safetyStock;
        }

        
        public void setMaxStock(final Integer maxStock) {
            this.maxStock = maxStock;
        }

        
        public void setLocation(final String location) {
            this.location = location;
        }

        
        public void setWarehouseCode(final String warehouseCode) {
            this.warehouseCode = warehouseCode;
        }

        
        public void setShelfCode(final String shelfCode) {
            this.shelfCode = shelfCode;
        }

        
        public void setLayerCode(final String layerCode) {
            this.layerCode = layerCode;
        }

        
        public void setSupplierCode(final String supplierCode) {
            this.supplierCode = supplierCode;
        }

        
        public void setRemark(final String remark) {
            this.remark = remark;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof MaterialController.CreateMaterialReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$safetyStock = this.getSafetyStock();
                Object other$safetyStock = other.getSafetyStock();
                if (this$safetyStock == null ? other$safetyStock == null : this$safetyStock.equals(other$safetyStock)) {
                    Object this$maxStock = this.getMaxStock();
                    Object other$maxStock = other.getMaxStock();
                    if (this$maxStock == null ? other$maxStock == null : this$maxStock.equals(other$maxStock)) {
                        Object this$materialCode = this.getMaterialCode();
                        Object other$materialCode = other.getMaterialCode();
                        if (this$materialCode == null ? other$materialCode == null : this$materialCode.equals(other$materialCode)) {
                            Object this$name = this.getName();
                            Object other$name = other.getName();
                            if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                                Object this$category = this.getCategory();
                                Object other$category = other.getCategory();
                                if (this$category == null ? other$category == null : this$category.equals(other$category)) {
                                    Object this$type = this.getType();
                                    Object other$type = other.getType();
                                    if (this$type == null ? other$type == null : this$type.equals(other$type)) {
                                        Object this$brand = this.getBrand();
                                        Object other$brand = other.getBrand();
                                        if (this$brand == null ? other$brand == null : this$brand.equals(other$brand)) {
                                            Object this$model = this.getModel();
                                            Object other$model = other.getModel();
                                            if (this$model == null ? other$model == null : this$model.equals(other$model)) {
                                                Object this$pn = this.getPn();
                                                Object other$pn = other.getPn();
                                                if (this$pn == null ? other$pn == null : this$pn.equals(other$pn)) {
                                                    Object this$spec = this.getSpec();
                                                    Object other$spec = other.getSpec();
                                                    if (this$spec == null ? other$spec == null : this$spec.equals(other$spec)) {
                                                        Object this$unit = this.getUnit();
                                                        Object other$unit = other.getUnit();
                                                        if (this$unit == null ? other$unit == null : this$unit.equals(other$unit)) {
                                                            Object this$price = this.getPrice();
                                                            Object other$price = other.getPrice();
                                                            if (this$price == null ? other$price == null : this$price.equals(other$price)) {
                                                                Object this$location = this.getLocation();
                                                                Object other$location = other.getLocation();
                                                                if (this$location == null ? other$location == null : this$location.equals(other$location)) {
                                                                    Object this$warehouseCode = this.getWarehouseCode();
                                                                    Object other$warehouseCode = other.getWarehouseCode();
                                                                    if (this$warehouseCode == null
                                                                        ? other$warehouseCode == null
                                                                        : this$warehouseCode.equals(other$warehouseCode)) {
                                                                        Object this$shelfCode = this.getShelfCode();
                                                                        Object other$shelfCode = other.getShelfCode();
                                                                        if (this$shelfCode == null
                                                                            ? other$shelfCode == null
                                                                            : this$shelfCode.equals(other$shelfCode)) {
                                                                            Object this$layerCode = this.getLayerCode();
                                                                            Object other$layerCode = other.getLayerCode();
                                                                            if (this$layerCode == null
                                                                                ? other$layerCode == null
                                                                                : this$layerCode.equals(other$layerCode)) {
                                                                                Object this$supplierCode = this.getSupplierCode();
                                                                                Object other$supplierCode = other.getSupplierCode();
                                                                                if (this$supplierCode == null
                                                                                    ? other$supplierCode == null
                                                                                    : this$supplierCode.equals(other$supplierCode)) {
                                                                                    Object this$remark = this.getRemark();
                                                                                    Object other$remark = other.getRemark();
                                                                                    return this$remark == null
                                                                                        ? other$remark == null
                                                                                        : this$remark.equals(other$remark);
                                                                                } else {
                                                                                    return false;
                                                                                }
                                                                            } else {
                                                                                return false;
                                                                            }
                                                                        } else {
                                                                            return false;
                                                                        }
                                                                    } else {
                                                                        return false;
                                                                    }
                                                                } else {
                                                                    return false;
                                                                }
                                                            } else {
                                                                return false;
                                                            }
                                                        } else {
                                                            return false;
                                                        }
                                                    } else {
                                                        return false;
                                                    }
                                                } else {
                                                    return false;
                                                }
                                            } else {
                                                return false;
                                            }
                                        } else {
                                            return false;
                                        }
                                    } else {
                                        return false;
                                    }
                                } else {
                                    return false;
                                }
                            } else {
                                return false;
                            }
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof MaterialController.CreateMaterialReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $safetyStock = this.getSafetyStock();
            result = result * 59 + ($safetyStock == null ? 43 : $safetyStock.hashCode());
            Object $maxStock = this.getMaxStock();
            result = result * 59 + ($maxStock == null ? 43 : $maxStock.hashCode());
            Object $materialCode = this.getMaterialCode();
            result = result * 59 + ($materialCode == null ? 43 : $materialCode.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $category = this.getCategory();
            result = result * 59 + ($category == null ? 43 : $category.hashCode());
            Object $type = this.getType();
            result = result * 59 + ($type == null ? 43 : $type.hashCode());
            Object $brand = this.getBrand();
            result = result * 59 + ($brand == null ? 43 : $brand.hashCode());
            Object $model = this.getModel();
            result = result * 59 + ($model == null ? 43 : $model.hashCode());
            Object $pn = this.getPn();
            result = result * 59 + ($pn == null ? 43 : $pn.hashCode());
            Object $spec = this.getSpec();
            result = result * 59 + ($spec == null ? 43 : $spec.hashCode());
            Object $unit = this.getUnit();
            result = result * 59 + ($unit == null ? 43 : $unit.hashCode());
            Object $price = this.getPrice();
            result = result * 59 + ($price == null ? 43 : $price.hashCode());
            Object $location = this.getLocation();
            result = result * 59 + ($location == null ? 43 : $location.hashCode());
            Object $warehouseCode = this.getWarehouseCode();
            result = result * 59 + ($warehouseCode == null ? 43 : $warehouseCode.hashCode());
            Object $shelfCode = this.getShelfCode();
            result = result * 59 + ($shelfCode == null ? 43 : $shelfCode.hashCode());
            Object $layerCode = this.getLayerCode();
            result = result * 59 + ($layerCode == null ? 43 : $layerCode.hashCode());
            Object $supplierCode = this.getSupplierCode();
            result = result * 59 + ($supplierCode == null ? 43 : $supplierCode.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "MaterialController.CreateMaterialReq(materialCode="
                + this.getMaterialCode()
                + ", name="
                + this.getName()
                + ", category="
                + this.getCategory()
                + ", type="
                + this.getType()
                + ", brand="
                + this.getBrand()
                + ", model="
                + this.getModel()
                + ", pn="
                + this.getPn()
                + ", spec="
                + this.getSpec()
                + ", unit="
                + this.getUnit()
                + ", price="
                + this.getPrice()
                + ", safetyStock="
                + this.getSafetyStock()
                + ", maxStock="
                + this.getMaxStock()
                + ", location="
                + this.getLocation()
                + ", warehouseCode="
                + this.getWarehouseCode()
                + ", shelfCode="
                + this.getShelfCode()
                + ", layerCode="
                + this.getLayerCode()
                + ", supplierCode="
                + this.getSupplierCode()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }

    private static record LocationRef(String warehouseCode, String shelfCode, String layerCode) {
    }

    public static class UpdateMaterialReq {
        private Integer version;
        private String name;
        private String category;
        private String type;
        private String brand;
        private String model;
        private String pn;
        private String spec;
        private String unit;
        private BigDecimal price;
        private Integer safetyStock;
        private Integer maxStock;
        private String location;
        private String warehouseCode;
        private String shelfCode;
        private String layerCode;
        private String supplierCode;
        private String remark;
        private Integer stock;
        private Integer totalIn;
        private Integer totalOut;

        
        public Integer getVersion() {
            return this.version;
        }

        
        public String getName() {
            return this.name;
        }

        
        public String getCategory() {
            return this.category;
        }

        
        public String getType() {
            return this.type;
        }

        
        public String getBrand() {
            return this.brand;
        }

        
        public String getModel() {
            return this.model;
        }

        
        public String getPn() {
            return this.pn;
        }

        
        public String getSpec() {
            return this.spec;
        }

        
        public String getUnit() {
            return this.unit;
        }

        
        public BigDecimal getPrice() {
            return this.price;
        }

        
        public Integer getSafetyStock() {
            return this.safetyStock;
        }

        
        public Integer getMaxStock() {
            return this.maxStock;
        }

        
        public String getLocation() {
            return this.location;
        }

        
        public String getWarehouseCode() {
            return this.warehouseCode;
        }

        
        public String getShelfCode() {
            return this.shelfCode;
        }

        
        public String getLayerCode() {
            return this.layerCode;
        }

        
        public String getSupplierCode() {
            return this.supplierCode;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public Integer getStock() {
            return this.stock;
        }

        
        public Integer getTotalIn() {
            return this.totalIn;
        }

        
        public Integer getTotalOut() {
            return this.totalOut;
        }

        
        public void setVersion(final Integer version) {
            this.version = version;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setCategory(final String category) {
            this.category = category;
        }

        
        public void setType(final String type) {
            this.type = type;
        }

        
        public void setBrand(final String brand) {
            this.brand = brand;
        }

        
        public void setModel(final String model) {
            this.model = model;
        }

        
        public void setPn(final String pn) {
            this.pn = pn;
        }

        
        public void setSpec(final String spec) {
            this.spec = spec;
        }

        
        public void setUnit(final String unit) {
            this.unit = unit;
        }

        
        public void setPrice(final BigDecimal price) {
            this.price = price;
        }

        
        public void setSafetyStock(final Integer safetyStock) {
            this.safetyStock = safetyStock;
        }

        
        public void setMaxStock(final Integer maxStock) {
            this.maxStock = maxStock;
        }

        
        public void setLocation(final String location) {
            this.location = location;
        }

        
        public void setWarehouseCode(final String warehouseCode) {
            this.warehouseCode = warehouseCode;
        }

        
        public void setShelfCode(final String shelfCode) {
            this.shelfCode = shelfCode;
        }

        
        public void setLayerCode(final String layerCode) {
            this.layerCode = layerCode;
        }

        
        public void setSupplierCode(final String supplierCode) {
            this.supplierCode = supplierCode;
        }

        
        public void setRemark(final String remark) {
            this.remark = remark;
        }

        
        public void setStock(final Integer stock) {
            this.stock = stock;
        }

        
        public void setTotalIn(final Integer totalIn) {
            this.totalIn = totalIn;
        }

        
        public void setTotalOut(final Integer totalOut) {
            this.totalOut = totalOut;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof MaterialController.UpdateMaterialReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$version = this.getVersion();
                Object other$version = other.getVersion();
                if (this$version == null ? other$version == null : this$version.equals(other$version)) {
                    Object this$safetyStock = this.getSafetyStock();
                    Object other$safetyStock = other.getSafetyStock();
                    if (this$safetyStock == null ? other$safetyStock == null : this$safetyStock.equals(other$safetyStock)) {
                        Object this$maxStock = this.getMaxStock();
                        Object other$maxStock = other.getMaxStock();
                        if (this$maxStock == null ? other$maxStock == null : this$maxStock.equals(other$maxStock)) {
                            Object this$stock = this.getStock();
                            Object other$stock = other.getStock();
                            if (this$stock == null ? other$stock == null : this$stock.equals(other$stock)) {
                                Object this$totalIn = this.getTotalIn();
                                Object other$totalIn = other.getTotalIn();
                                if (this$totalIn == null ? other$totalIn == null : this$totalIn.equals(other$totalIn)) {
                                    Object this$totalOut = this.getTotalOut();
                                    Object other$totalOut = other.getTotalOut();
                                    if (this$totalOut == null ? other$totalOut == null : this$totalOut.equals(other$totalOut)) {
                                        Object this$name = this.getName();
                                        Object other$name = other.getName();
                                        if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                                            Object this$category = this.getCategory();
                                            Object other$category = other.getCategory();
                                            if (this$category == null ? other$category == null : this$category.equals(other$category)) {
                                                Object this$type = this.getType();
                                                Object other$type = other.getType();
                                                if (this$type == null ? other$type == null : this$type.equals(other$type)) {
                                                    Object this$brand = this.getBrand();
                                                    Object other$brand = other.getBrand();
                                                    if (this$brand == null ? other$brand == null : this$brand.equals(other$brand)) {
                                                        Object this$model = this.getModel();
                                                        Object other$model = other.getModel();
                                                        if (this$model == null ? other$model == null : this$model.equals(other$model)) {
                                                            Object this$pn = this.getPn();
                                                            Object other$pn = other.getPn();
                                                            if (this$pn == null ? other$pn == null : this$pn.equals(other$pn)) {
                                                                Object this$spec = this.getSpec();
                                                                Object other$spec = other.getSpec();
                                                                if (this$spec == null ? other$spec == null : this$spec.equals(other$spec)) {
                                                                    Object this$unit = this.getUnit();
                                                                    Object other$unit = other.getUnit();
                                                                    if (this$unit == null ? other$unit == null : this$unit.equals(other$unit)) {
                                                                        Object this$price = this.getPrice();
                                                                        Object other$price = other.getPrice();
                                                                        if (this$price == null ? other$price == null : this$price.equals(other$price)) {
                                                                            Object this$location = this.getLocation();
                                                                            Object other$location = other.getLocation();
                                                                            if (this$location == null
                                                                                ? other$location == null
                                                                                : this$location.equals(other$location)) {
                                                                                Object this$warehouseCode = this.getWarehouseCode();
                                                                                Object other$warehouseCode = other.getWarehouseCode();
                                                                                if (this$warehouseCode == null
                                                                                    ? other$warehouseCode == null
                                                                                    : this$warehouseCode.equals(other$warehouseCode)) {
                                                                                    Object this$shelfCode = this.getShelfCode();
                                                                                    Object other$shelfCode = other.getShelfCode();
                                                                                    if (this$shelfCode == null
                                                                                        ? other$shelfCode == null
                                                                                        : this$shelfCode.equals(other$shelfCode)) {
                                                                                        Object this$layerCode = this.getLayerCode();
                                                                                        Object other$layerCode = other.getLayerCode();
                                                                                        if (this$layerCode == null
                                                                                            ? other$layerCode == null
                                                                                            : this$layerCode.equals(other$layerCode)) {
                                                                                            Object this$supplierCode = this.getSupplierCode();
                                                                                            Object other$supplierCode = other.getSupplierCode();
                                                                                            if (this$supplierCode == null
                                                                                                ? other$supplierCode == null
                                                                                                : this$supplierCode.equals(other$supplierCode)) {
                                                                                                Object this$remark = this.getRemark();
                                                                                                Object other$remark = other.getRemark();
                                                                                                return this$remark == null
                                                                                                    ? other$remark == null
                                                                                                    : this$remark.equals(other$remark);
                                                                                            } else {
                                                                                                return false;
                                                                                            }
                                                                                        } else {
                                                                                            return false;
                                                                                        }
                                                                                    } else {
                                                                                        return false;
                                                                                    }
                                                                                } else {
                                                                                    return false;
                                                                                }
                                                                            } else {
                                                                                return false;
                                                                            }
                                                                        } else {
                                                                            return false;
                                                                        }
                                                                    } else {
                                                                        return false;
                                                                    }
                                                                } else {
                                                                    return false;
                                                                }
                                                            } else {
                                                                return false;
                                                            }
                                                        } else {
                                                            return false;
                                                        }
                                                    } else {
                                                        return false;
                                                    }
                                                } else {
                                                    return false;
                                                }
                                            } else {
                                                return false;
                                            }
                                        } else {
                                            return false;
                                        }
                                    } else {
                                        return false;
                                    }
                                } else {
                                    return false;
                                }
                            } else {
                                return false;
                            }
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof MaterialController.UpdateMaterialReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $version = this.getVersion();
            result = result * 59 + ($version == null ? 43 : $version.hashCode());
            Object $safetyStock = this.getSafetyStock();
            result = result * 59 + ($safetyStock == null ? 43 : $safetyStock.hashCode());
            Object $maxStock = this.getMaxStock();
            result = result * 59 + ($maxStock == null ? 43 : $maxStock.hashCode());
            Object $stock = this.getStock();
            result = result * 59 + ($stock == null ? 43 : $stock.hashCode());
            Object $totalIn = this.getTotalIn();
            result = result * 59 + ($totalIn == null ? 43 : $totalIn.hashCode());
            Object $totalOut = this.getTotalOut();
            result = result * 59 + ($totalOut == null ? 43 : $totalOut.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $category = this.getCategory();
            result = result * 59 + ($category == null ? 43 : $category.hashCode());
            Object $type = this.getType();
            result = result * 59 + ($type == null ? 43 : $type.hashCode());
            Object $brand = this.getBrand();
            result = result * 59 + ($brand == null ? 43 : $brand.hashCode());
            Object $model = this.getModel();
            result = result * 59 + ($model == null ? 43 : $model.hashCode());
            Object $pn = this.getPn();
            result = result * 59 + ($pn == null ? 43 : $pn.hashCode());
            Object $spec = this.getSpec();
            result = result * 59 + ($spec == null ? 43 : $spec.hashCode());
            Object $unit = this.getUnit();
            result = result * 59 + ($unit == null ? 43 : $unit.hashCode());
            Object $price = this.getPrice();
            result = result * 59 + ($price == null ? 43 : $price.hashCode());
            Object $location = this.getLocation();
            result = result * 59 + ($location == null ? 43 : $location.hashCode());
            Object $warehouseCode = this.getWarehouseCode();
            result = result * 59 + ($warehouseCode == null ? 43 : $warehouseCode.hashCode());
            Object $shelfCode = this.getShelfCode();
            result = result * 59 + ($shelfCode == null ? 43 : $shelfCode.hashCode());
            Object $layerCode = this.getLayerCode();
            result = result * 59 + ($layerCode == null ? 43 : $layerCode.hashCode());
            Object $supplierCode = this.getSupplierCode();
            result = result * 59 + ($supplierCode == null ? 43 : $supplierCode.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "MaterialController.UpdateMaterialReq(version="
                + this.getVersion()
                + ", name="
                + this.getName()
                + ", category="
                + this.getCategory()
                + ", type="
                + this.getType()
                + ", brand="
                + this.getBrand()
                + ", model="
                + this.getModel()
                + ", pn="
                + this.getPn()
                + ", spec="
                + this.getSpec()
                + ", unit="
                + this.getUnit()
                + ", price="
                + this.getPrice()
                + ", safetyStock="
                + this.getSafetyStock()
                + ", maxStock="
                + this.getMaxStock()
                + ", location="
                + this.getLocation()
                + ", warehouseCode="
                + this.getWarehouseCode()
                + ", shelfCode="
                + this.getShelfCode()
                + ", layerCode="
                + this.getLayerCode()
                + ", supplierCode="
                + this.getSupplierCode()
                + ", remark="
                + this.getRemark()
                + ", stock="
                + this.getStock()
                + ", totalIn="
                + this.getTotalIn()
                + ", totalOut="
                + this.getTotalOut()
                + ")";
        }
    }
}
