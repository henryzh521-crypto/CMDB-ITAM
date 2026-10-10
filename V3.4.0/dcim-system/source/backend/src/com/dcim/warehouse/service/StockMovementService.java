package com.dcim.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.BusinessException;
import com.dcim.common.seq.SeqNoGenerator;
import com.dcim.common.stock.StockMovementPort;
import com.dcim.warehouse.entity.MaterialEntity;
import com.dcim.warehouse.entity.StockLogEntity;
import com.dcim.warehouse.entity.WarehouseEntity;
import com.dcim.warehouse.mapper.MaterialMapper;
import com.dcim.warehouse.mapper.StockLogMapper;
import com.dcim.warehouse.mapper.WarehouseMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockMovementService {
    private static final Set<String> TYPES = Set.of("IN", "OUT", "ADJUST", "TRANSFER");
    private final MaterialMapper materialMapper;
    private final StockLogMapper stockLogMapper;
    private final WarehouseMapper warehouseMapper;
    private final SeqNoGenerator seqNoGenerator;
    private final JdbcTemplate jdbc;

    @Transactional
    public StockMovementPort.MovementResult apply(StockMovementPort.MovementCmd cmd) {
        String logType = cmd.logType() == null ? "" : cmd.logType().trim().toUpperCase();
        if (!TYPES.contains(logType)) {
            throw BusinessException.badRequest("移动类型无效，仅支持 IN/OUT/ADJUST/TRANSFER：" + cmd.logType());
        } else if (cmd.materialCode() != null && !cmd.materialCode().isBlank()) {
            int qty = cmd.qty();
            if ("ADJUST".equals(logType)) {
                if (qty < 0) {
                    throw BusinessException.badRequest("校准目标库存不能为负数");
                }
            } else if (qty < 1) {
                throw BusinessException.badRequest("数量必须为正整数");
            }

            MaterialEntity m = (MaterialEntity)this.materialMapper
                .selectOne(
                    (Wrappers.<MaterialEntity>lambdaQuery().eq(MaterialEntity::getMaterialCode, cmd.materialCode().trim())).last("FOR UPDATE")
                );
            if (m == null) {
                throw BusinessException.badRequest("物料不存在：" + cmd.materialCode().trim());
            } else {
                int stock = this.nz(m.getStock());
                int newTotalIn = this.nz(m.getTotalIn());
                int newTotalOut = this.nz(m.getTotalOut());
                int whBefore = 0;
                int whAfter = 0;
                String wh = null;
                String fromWh = null;
                String remark = cmd.remark();
                int newStock;
                switch (logType) {
                    case "IN":
                        wh = this.resolveWarehouse(m, cmd.warehouseCode());
                        whBefore = this.levelQty(m.getMaterialCode(), wh);
                        whAfter = whBefore + qty;
                        newStock = stock + qty;
                        newTotalIn += qty;
                        this.addLevel(m.getMaterialCode(), wh, qty);
                        break;
                    case "OUT":
                        wh = this.resolveWarehouse(m, cmd.warehouseCode());
                        whBefore = this.levelQty(m.getMaterialCode(), wh);
                        this.decrementLevel(m, wh, qty);
                        whAfter = whBefore - qty;
                        newStock = stock - qty;
                        newTotalOut += qty;
                        break;
                    case "ADJUST":
                        wh = this.resolveWarehouse(m, cmd.warehouseCode());
                        whBefore = this.levelQty(m.getMaterialCode(), wh);
                        int diff = qty - whBefore;
                        if (diff > 0) {
                            newTotalIn += diff;
                        } else if (diff < 0) {
                            newTotalOut -= diff;
                        }

                        newStock = stock + diff;
                        whAfter = qty;
                        this.setLevel(m.getMaterialCode(), wh, qty);
                        String calib = "校准 自 " + whBefore + " 至 " + qty;
                        remark = remark != null && !remark.isBlank() ? remark.trim() + "；" + calib : calib;
                        break;
                    default:
                        fromWh = this.requireWarehouse(cmd.fromWarehouseCode(), "转出仓库不能为空");
                        wh = this.requireWarehouse(cmd.warehouseCode(), "转入仓库不能为空");
                        if (fromWh.equals(wh)) {
                            throw BusinessException.badRequest("转出仓库与转入仓库不能相同：" + fromWh);
                        }

                        whBefore = this.levelQty(m.getMaterialCode(), wh);
                        this.decrementLevel(m, fromWh, qty);
                        this.addLevel(m.getMaterialCode(), wh, qty);
                        whAfter = whBefore + qty;
                        newStock = stock;
                }

                // 修复 A3：所有写 material.stock 的路径统一断言非负——OUT 分支此前只校验分仓库存，
                // material.stock 与分仓合计漂移时会把总库存写负；IN 分支相加溢出时结果同样为负，此处一并拦截
                if (newStock < 0) {
                    throw BusinessException.badRequest(
                        "物料「" + m.getName() + "」（" + m.getMaterialCode() + "）总库存不允许为负：当前 " + stock + "，本次变动后为 " + newStock
                    );
                }

                this.jdbc
                    .update(
                        "UPDATE material SET stock = ?, total_in = ?, total_out = ?, updated_at = now() WHERE material_code = ?",
                        new Object[]{newStock, newTotalIn, newTotalOut, m.getMaterialCode()}
                    );
                StockLogEntity log = new StockLogEntity();
                log.setLogNo(this.seqNoGenerator.next("IO-" + SeqNoGenerator.todaySegment(), 4));
                log.setLogType(logType);
                log.setMaterialCode(m.getMaterialCode());
                log.setQty(qty);
                log.setBizDate(cmd.bizDate() == null ? LocalDate.now() : cmd.bizDate());
                log.setOperator(cmd.operator());
                log.setDepartment(cmd.department());
                log.setRefWo(cmd.refWo());
                log.setWarehouseCode(wh);
                log.setFromWarehouseCode(fromWh);
                log.setRemark(remark);
                log.setCreatedAt(LocalDateTime.now());
                this.stockLogMapper.insert(log);
                return new StockMovementPort.MovementResult(log.getLogNo(), m.getMaterialCode(), logType, qty, newStock, remark, wh, whAfter);
            }
        } else {
            throw BusinessException.badRequest("物料编码不能为空");
        }
    }

    public String resolveWarehouse(MaterialEntity m, String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            String code = explicit.trim();
            if (this.selectWarehouse(code) == null) {
                throw BusinessException.badRequest("仓库不存在：" + code);
            } else {
                return code;
            }
        } else if (m.getWarehouseCode() != null && !m.getWarehouseCode().isBlank()) {
            return m.getWarehouseCode().trim();
        } else {
            WarehouseEntity def = (WarehouseEntity)this.warehouseMapper.selectOne(Wrappers.<WarehouseEntity>lambdaQuery().eq(WarehouseEntity::getIsDefault, true));
            if (def == null) {
                throw BusinessException.badRequest("物料「" + m.getName() + "」未指定默认仓库，且系统未设置默认仓，请先设置");
            } else {
                return def.getWarehouseCode();
            }
        }
    }

    private String requireWarehouse(String raw, String messageIfBlank) {
        if (raw != null && !raw.isBlank()) {
            String code = raw.trim();
            if (this.selectWarehouse(code) == null) {
                throw BusinessException.badRequest("仓库不存在：" + code);
            } else {
                return code;
            }
        } else {
            throw BusinessException.badRequest(messageIfBlank);
        }
    }

    private WarehouseEntity selectWarehouse(String code) {
        return (WarehouseEntity)this.warehouseMapper.selectOne(Wrappers.<WarehouseEntity>lambdaQuery().eq(WarehouseEntity::getWarehouseCode, code));
    }

    private int levelQty(String materialCode, String warehouseCode) {
        List<Integer> rows = this.jdbc
            .queryForList(
                "SELECT qty FROM stock_level WHERE material_code = ? AND warehouse_code = ?", Integer.class, new Object[]{materialCode, warehouseCode}
            );
        return !rows.isEmpty() && rows.get(0) != null ? rows.get(0) : 0;
    }

    private void addLevel(String materialCode, String warehouseCode, int delta) {
        this.jdbc
            .update(
                "INSERT INTO stock_level (material_code, warehouse_code, qty) VALUES (?, ?, ?) ON CONFLICT (material_code, warehouse_code) DO UPDATE SET qty = stock_level.qty + EXCLUDED.qty, version = stock_level.version + 1, updated_at = now()",
                new Object[]{materialCode, warehouseCode, delta}
            );
    }

    private void setLevel(String materialCode, String warehouseCode, int target) {
        this.jdbc
            .update(
                "INSERT INTO stock_level (material_code, warehouse_code, qty) VALUES (?, ?, ?) ON CONFLICT (material_code, warehouse_code) DO UPDATE SET qty = EXCLUDED.qty, version = stock_level.version + 1, updated_at = now()",
                new Object[]{materialCode, warehouseCode, target}
            );
    }

    private void decrementLevel(MaterialEntity m, String warehouseCode, int qty) {
        int rows = this.jdbc
            .update(
                "UPDATE stock_level SET qty = qty - ?, version = version + 1, updated_at = now() WHERE material_code = ? AND warehouse_code = ? AND qty >= ?",
                new Object[]{qty, m.getMaterialCode(), warehouseCode, qty}
            );
        if (rows == 0) {
            int cur = this.levelQty(m.getMaterialCode(), warehouseCode);
            String unit = m.getUnit() == null ? "" : m.getUnit();
            throw BusinessException.conflict("库存不足：" + m.getName() + " 当前库存 " + cur + unit + "，本次需领用 " + qty + unit);
        }
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    
    public StockMovementService(
        final MaterialMapper materialMapper,
        final StockLogMapper stockLogMapper,
        final WarehouseMapper warehouseMapper,
        final SeqNoGenerator seqNoGenerator,
        final JdbcTemplate jdbc
    ) {
        this.materialMapper = materialMapper;
        this.stockLogMapper = stockLogMapper;
        this.warehouseMapper = warehouseMapper;
        this.seqNoGenerator = seqNoGenerator;
        this.jdbc = jdbc;
    }
}
