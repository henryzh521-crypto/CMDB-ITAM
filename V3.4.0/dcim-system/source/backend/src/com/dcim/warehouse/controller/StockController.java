package com.dcim.warehouse.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.common.stock.StockMovementPort;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import com.dcim.warehouse.entity.MaterialEntity;
import com.dcim.warehouse.entity.StockLogEntity;
import com.dcim.warehouse.mapper.MaterialMapper;
import com.dcim.warehouse.mapper.StockLogMapper;
import com.dcim.warehouse.service.StockMovementService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/stock-logs"})
public class StockController {
    private static final Set<String> TYPES = Set.of("IN", "OUT", "ADJUST", "TRANSFER");
    private final StockMovementService stockMovementService;
    private final MaterialMapper materialMapper;
    private final StockLogMapper stockLogMapper;
    private final JdbcTemplate jdbc;

    @PostMapping
    @RequirePerm(
        module = "stockLogs",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "stockLogs",
        objType = "stock_log",
        objIdSpEL = "#req.materialCode"
    )
    public ApiResult<Map<String, Object>> create(@RequestBody @Valid StockController.MovementReq req) {
        LoginUser u = CurrentUser.get();
        StockMovementPort.MovementCmd cmd = this.toCmd(req, u == null ? null : u.username());
        StockMovementPort.MovementResult r = this.stockMovementService.apply(cmd);
        return ApiResult.ok(this.movementOut(cmd, r));
    }

    @PostMapping({"/batch"})
    @RequirePerm(
        module = "stockLogs",
        write = true,
        action = "create"
    )
    @Audit(
        action = "BATCH_CREATE",
        module = "stockLogs",
        objType = "stock_log"
    )
    @Transactional
    public ApiResult<Map<String, Object>> batch(@RequestBody @Valid StockController.BatchReq req) {
        List<StockController.MovementReq> items = req.getItems();
        if (items != null && !items.isEmpty()) {
            LoginUser u = CurrentUser.get();
            String operator = u == null ? null : u.username();
            Map<String, Integer> projected = new HashMap<>();
            List<StockMovementPort.MovementCmd> cmds = new ArrayList<>();

            for (int i = 0; i < items.size(); i++) {
                String prefix = "第 " + (i + 1) + " 行：";

                StockMovementPort.MovementCmd cmd;
                try {
                    cmd = this.toCmd(items.get(i), operator);
                } catch (BusinessException var17) {
                    throw new BusinessException(var17.getCode(), prefix + var17.getMessage());
                }

                MaterialEntity m = (MaterialEntity)this.materialMapper
                    .selectOne(Wrappers.<MaterialEntity>lambdaQuery().eq(MaterialEntity::getMaterialCode, cmd.materialCode()));
                if (m == null) {
                    throw BusinessException.badRequest(prefix + "物料不存在：" + cmd.materialCode());
                }

                String unit = m.getUnit() == null ? "" : m.getUnit();
                if ("TRANSFER".equals(cmd.logType())) {
                    String fromWh = this.requireNonBlank(cmd.fromWarehouseCode(), prefix + "转出仓库不能为空");
                    String toWh = this.requireNonBlank(cmd.warehouseCode(), prefix + "转入仓库不能为空");
                    if (fromWh.equals(toWh)) {
                        throw BusinessException.badRequest(prefix + "转出仓库与转入仓库不能相同：" + fromWh);
                    }

                    String srcKey = this.key(cmd.materialCode(), fromWh);
                    int cur = projected.computeIfAbsent(srcKey, k -> this.levelQty(cmd.materialCode(), fromWh));
                    if (cmd.qty() > cur) {
                        throw BusinessException.conflict(prefix + "库存不足：" + m.getName() + " 当前库存 " + cur + unit + "，本次需领用 " + cmd.qty() + unit);
                    }

                    projected.put(srcKey, cur - cmd.qty());
                    String dstKey = this.key(cmd.materialCode(), toWh);
                    projected.put(dstKey, projected.getOrDefault(dstKey, this.levelQty(cmd.materialCode(), toWh)) + cmd.qty());
                    cmds.add(cmd);
                } else {
                    String wh = this.stockMovementService.resolveWarehouse(m, cmd.warehouseCode());
                    String pKey = this.key(cmd.materialCode(), wh);
                    int cur = projected.computeIfAbsent(pKey, k -> this.levelQty(cmd.materialCode(), wh));
                    if ("OUT".equals(cmd.logType()) && cmd.qty() > cur) {
                        throw BusinessException.conflict(prefix + "库存不足：" + m.getName() + " 当前库存 " + cur + unit + "，本次需领用 " + cmd.qty() + unit);
                    }

                    String var27 = cmd.logType();

                    projected.put(pKey, switch (var27) {
                        case "IN" -> cur + cmd.qty();
                        case "OUT" -> cur - cmd.qty();
                        default -> cmd.qty();
                    });
                    cmds.add(cmd);
                }
            }

            List<Map<String, Object>> logs = new ArrayList<>();

            // 修复 A6：进入逐行加锁循环前统一按物料编码排序，消除多行锁顺序不定导致的死锁面
            cmds.sort(Comparator.comparing(StockMovementPort.MovementCmd::materialCode));
            for (StockMovementPort.MovementCmd cmdx : cmds) {
                StockMovementPort.MovementResult r = this.stockMovementService.apply(cmdx);
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("logNo", r.logNo());
                l.put("materialCode", r.materialCode());
                l.put("logType", r.logType());
                l.put("qty", r.qty());
                logs.add(l);
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("count", logs.size());
            out.put("logs", logs);
            return ApiResult.ok(out);
        } else {
            throw BusinessException.badRequest("批量明细不能为空");
        }
    }

    @GetMapping
    @RequirePerm(
        module = "stockLogs"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String logType,
        @RequestParam(required = false) String materialCode,
        @RequestParam(required = false) String bizDateFrom,
        @RequestParam(required = false) String bizDateTo,
        @RequestParam(required = false) String warehouseCode,
        @RequestParam(required = false) String keyword
    ) {
        LambdaQueryWrapper<StockLogEntity> qw = Wrappers.lambdaQuery();
        if (logType != null && !logType.isBlank()) {
            qw.eq(StockLogEntity::getLogType, logType.trim().toUpperCase());
        }

        if (materialCode != null && !materialCode.isBlank()) {
            qw.eq(StockLogEntity::getMaterialCode, materialCode.trim());
        }

        if (warehouseCode != null && !warehouseCode.isBlank()) {
            String code = warehouseCode.trim();
            qw.and(
                w -> ((w.eq(StockLogEntity::getWarehouseCode, code)).or())
                        .eq(StockLogEntity::getFromWarehouseCode, code)
            );
        }

        LocalDate from = this.parseDate(bizDateFrom, "bizDateFrom");
        LocalDate to = this.parseDate(bizDateTo, "bizDateTo");
        if (from != null) {
            qw.ge(StockLogEntity::getBizDate, from);
        }

        if (to != null) {
            qw.le(StockLogEntity::getBizDate, to);
        }

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(
                w -> ((((w.like(StockLogEntity::getLogNo, kw)).or())
                                .like(StockLogEntity::getRefWo, kw))
                            .or())
                        .like(StockLogEntity::getOperator, kw)
            );
        }

        (qw.orderByDesc(StockLogEntity::getCreatedAt)).orderByDesc(StockLogEntity::getId);
        Page<StockLogEntity> p = new Page(page, size);
        this.stockLogMapper.selectPage(p, qw);
        return ApiResult.ok(PageResult.of(p, this::logRow));
    }

    @GetMapping({"/materials-options"})
    @RequirePerm(
        module = "stockLogs"
    )
    public ApiResult<List<Map<String, Object>>> materialsOptions(@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "50") long size) {
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

        (qw.orderByAsc(MaterialEntity::getMaterialCode)).last("LIMIT " + Math.max(1L, Math.min(size, 200L)));
        List<Map<String, Object>> rows = new ArrayList<>();

        for (MaterialEntity m : this.materialMapper.selectList(qw)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialCode", m.getMaterialCode());
            row.put("name", m.getName());
            row.put("unit", m.getUnit());
            row.put("stock", m.getStock());
            row.put("warehouseCode", m.getWarehouseCode());
            row.put("shelfCode", m.getShelfCode());
            row.put("layerCode", m.getLayerCode());
            rows.add(row);
        }

        if (!rows.isEmpty()) {
            List<String> codes = rows.stream().map(rx -> String.valueOf(rx.get("materialCode"))).toList();
            String in = String.join(",", Collections.nCopies(codes.size(), "?"));
            Map<String, Map<String, Object>> byCode = new HashMap<>();

            for (Map<String, Object> r : this.jdbc
                .queryForList(
                    "SELECT material_code AS \"mc\", warehouse_code AS \"wc\", qty FROM stock_level WHERE material_code IN (" + in + ")", codes.toArray()
                )) {
                byCode.computeIfAbsent(String.valueOf(r.get("mc")), k -> new LinkedHashMap<>())
                    .put(String.valueOf(r.get("wc")), r.get("qty") == null ? 0 : ((Number)r.get("qty")).intValue());
            }

            for (Map<String, Object> r : rows) {
                r.put("warehouseStocks", byCode.getOrDefault(String.valueOf(r.get("materialCode")), new LinkedHashMap<>()));
            }
        }

        return ApiResult.ok(rows);
    }

    private StockMovementPort.MovementCmd toCmd(StockController.MovementReq req, String operator) {
        if (req == null) {
            throw BusinessException.badRequest("明细不能为空");
        } else {
            String type = req.getLogType() == null ? "" : req.getLogType().trim().toUpperCase();
            if (!TYPES.contains(type)) {
                throw BusinessException.badRequest("移动类型无效，仅支持 IN/OUT/ADJUST/TRANSFER：" + req.getLogType());
            } else if (req.getMaterialCode() != null && !req.getMaterialCode().isBlank()) {
                int q = this.toIntQty(req.getQty());
                if ("ADJUST".equals(type)) {
                    if (q < 0) {
                        throw BusinessException.badRequest("校准目标库存不能为负数");
                    }
                } else if (q < 1) {
                    throw BusinessException.badRequest("数量必须为正整数");
                }

                return new StockMovementPort.MovementCmd(
                    type,
                    req.getMaterialCode().trim(),
                    q,
                    this.parseDate(req.getBizDate(), "业务日期"),
                    this.blankToNull(req.getDepartment()),
                    this.blankToNull(req.getRefWo()),
                    operator,
                    req.getRemark(),
                    this.blankToNull(req.getWarehouseCode()),
                    this.blankToNull(req.getFromWarehouseCode())
                );
            } else {
                throw BusinessException.badRequest("物料编码不能为空");
            }
        }
    }

    private String key(String materialCode, String warehouseCode) {
        return materialCode + "|" + warehouseCode;
    }

    private int levelQty(String materialCode, String warehouseCode) {
        List<Integer> rows = this.jdbc
            .queryForList(
                "SELECT qty FROM stock_level WHERE material_code = ? AND warehouse_code = ?", Integer.class, new Object[]{materialCode, warehouseCode}
            );
        return !rows.isEmpty() && rows.get(0) != null ? rows.get(0) : 0;
    }

    private String requireNonBlank(String s, String message) {
        if (s != null && !s.isBlank()) {
            return s.trim();
        } else {
            throw BusinessException.badRequest(message);
        }
    }

    private int toIntQty(BigDecimal qty) {
        if (qty == null) {
            throw BusinessException.badRequest("数量不能为空");
        } else {
            BigDecimal stripped = qty.stripTrailingZeros();
            if (stripped.scale() > 0) {
                throw BusinessException.badRequest("数量必须为正整数");
            } else {
                // 修复 A3：加业务上限防 int 溢出——超界值落进 intValueExact 会抛
                // ArithmeticException 并以 500 返回，而不是可读的业务错误
                if (stripped.compareTo(BigDecimal.valueOf(1_000_000_000L)) > 0) {
                    throw BusinessException.badRequest("数量超出业务上限 1,000,000,000：" + stripped.toBigInteger());
                }

                return stripped.intValueExact();
            }
        }
    }

    private LocalDate parseDate(String s, String label) {
        if (s != null && !s.isBlank()) {
            try {
                return LocalDate.parse(s.trim());
            } catch (DateTimeParseException var4) {
                throw BusinessException.badRequest(label + "格式错误，应为 yyyy-MM-dd：" + s.trim());
            }
        } else {
            return null;
        }
    }

    private String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private Map<String, Object> movementOut(StockMovementPort.MovementCmd cmd, StockMovementPort.MovementResult r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("logNo", r.logNo());
        out.put("logType", r.logType());
        out.put("materialCode", r.materialCode());
        out.put("qty", r.qty());
        out.put("stockAfter", r.stockAfter());
        out.put("warehouseCode", r.warehouseCode());
        out.put("warehouseQtyAfter", r.warehouseQtyAfter());
        out.put("fromWarehouseCode", cmd.fromWarehouseCode());
        out.put("bizDate", cmd.bizDate() == null ? LocalDate.now().toString() : cmd.bizDate().toString());
        out.put("operator", cmd.operator());
        out.put("department", cmd.department());
        out.put("refWo", cmd.refWo());
        out.put("remark", r.remark());
        return out;
    }

    private Map<String, Object> logRow(StockLogEntity e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("logNo", e.getLogNo());
        row.put("logType", e.getLogType());
        row.put("materialCode", e.getMaterialCode());
        row.put("qty", e.getQty());
        row.put("bizDate", e.getBizDate() == null ? "" : e.getBizDate().toString());
        row.put("operator", e.getOperator());
        row.put("department", e.getDepartment());
        row.put("refWo", e.getRefWo());
        row.put("warehouseCode", e.getWarehouseCode());
        row.put("fromWarehouseCode", e.getFromWarehouseCode());
        row.put("remark", e.getRemark());
        row.put("createdAt", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
        return row;
    }

    
    public StockController(
        final StockMovementService stockMovementService, final MaterialMapper materialMapper, final StockLogMapper stockLogMapper, final JdbcTemplate jdbc
    ) {
        this.stockMovementService = stockMovementService;
        this.materialMapper = materialMapper;
        this.stockLogMapper = stockLogMapper;
        this.jdbc = jdbc;
    }

    public static class BatchReq {
        private List<StockController.MovementReq> items;

        
        public List<StockController.MovementReq> getItems() {
            return this.items;
        }

        
        public void setItems(final List<StockController.MovementReq> items) {
            this.items = items;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof StockController.BatchReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$items = this.getItems();
                Object other$items = other.getItems();
                return this$items == null ? other$items == null : this$items.equals(other$items);
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof StockController.BatchReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $items = this.getItems();
            return result * 59 + ($items == null ? 43 : $items.hashCode());
        }

        
        @Override
        public String toString() {
            return "StockController.BatchReq(items=" + this.getItems() + ")";
        }
    }

    public static class MovementReq {
        private String logType;
        private String materialCode;
        private BigDecimal qty;
        private String bizDate;
        private String department;
        private String refWo;
        private String remark;
        private String warehouseCode;
        private String fromWarehouseCode;

        
        public String getLogType() {
            return this.logType;
        }

        
        public String getMaterialCode() {
            return this.materialCode;
        }

        
        public BigDecimal getQty() {
            return this.qty;
        }

        
        public String getBizDate() {
            return this.bizDate;
        }

        
        public String getDepartment() {
            return this.department;
        }

        
        public String getRefWo() {
            return this.refWo;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public String getWarehouseCode() {
            return this.warehouseCode;
        }

        
        public String getFromWarehouseCode() {
            return this.fromWarehouseCode;
        }

        
        public void setLogType(final String logType) {
            this.logType = logType;
        }

        
        public void setMaterialCode(final String materialCode) {
            this.materialCode = materialCode;
        }

        
        public void setQty(final BigDecimal qty) {
            this.qty = qty;
        }

        
        public void setBizDate(final String bizDate) {
            this.bizDate = bizDate;
        }

        
        public void setDepartment(final String department) {
            this.department = department;
        }

        
        public void setRefWo(final String refWo) {
            this.refWo = refWo;
        }

        
        public void setRemark(final String remark) {
            this.remark = remark;
        }

        
        public void setWarehouseCode(final String warehouseCode) {
            this.warehouseCode = warehouseCode;
        }

        
        public void setFromWarehouseCode(final String fromWarehouseCode) {
            this.fromWarehouseCode = fromWarehouseCode;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof StockController.MovementReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$logType = this.getLogType();
                Object other$logType = other.getLogType();
                if (this$logType == null ? other$logType == null : this$logType.equals(other$logType)) {
                    Object this$materialCode = this.getMaterialCode();
                    Object other$materialCode = other.getMaterialCode();
                    if (this$materialCode == null ? other$materialCode == null : this$materialCode.equals(other$materialCode)) {
                        Object this$qty = this.getQty();
                        Object other$qty = other.getQty();
                        if (this$qty == null ? other$qty == null : this$qty.equals(other$qty)) {
                            Object this$bizDate = this.getBizDate();
                            Object other$bizDate = other.getBizDate();
                            if (this$bizDate == null ? other$bizDate == null : this$bizDate.equals(other$bizDate)) {
                                Object this$department = this.getDepartment();
                                Object other$department = other.getDepartment();
                                if (this$department == null ? other$department == null : this$department.equals(other$department)) {
                                    Object this$refWo = this.getRefWo();
                                    Object other$refWo = other.getRefWo();
                                    if (this$refWo == null ? other$refWo == null : this$refWo.equals(other$refWo)) {
                                        Object this$remark = this.getRemark();
                                        Object other$remark = other.getRemark();
                                        if (this$remark == null ? other$remark == null : this$remark.equals(other$remark)) {
                                            Object this$warehouseCode = this.getWarehouseCode();
                                            Object other$warehouseCode = other.getWarehouseCode();
                                            if (this$warehouseCode == null ? other$warehouseCode == null : this$warehouseCode.equals(other$warehouseCode)) {
                                                Object this$fromWarehouseCode = this.getFromWarehouseCode();
                                                Object other$fromWarehouseCode = other.getFromWarehouseCode();
                                                return this$fromWarehouseCode == null
                                                    ? other$fromWarehouseCode == null
                                                    : this$fromWarehouseCode.equals(other$fromWarehouseCode);
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
            return other instanceof StockController.MovementReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $logType = this.getLogType();
            result = result * 59 + ($logType == null ? 43 : $logType.hashCode());
            Object $materialCode = this.getMaterialCode();
            result = result * 59 + ($materialCode == null ? 43 : $materialCode.hashCode());
            Object $qty = this.getQty();
            result = result * 59 + ($qty == null ? 43 : $qty.hashCode());
            Object $bizDate = this.getBizDate();
            result = result * 59 + ($bizDate == null ? 43 : $bizDate.hashCode());
            Object $department = this.getDepartment();
            result = result * 59 + ($department == null ? 43 : $department.hashCode());
            Object $refWo = this.getRefWo();
            result = result * 59 + ($refWo == null ? 43 : $refWo.hashCode());
            Object $remark = this.getRemark();
            result = result * 59 + ($remark == null ? 43 : $remark.hashCode());
            Object $warehouseCode = this.getWarehouseCode();
            result = result * 59 + ($warehouseCode == null ? 43 : $warehouseCode.hashCode());
            Object $fromWarehouseCode = this.getFromWarehouseCode();
            return result * 59 + ($fromWarehouseCode == null ? 43 : $fromWarehouseCode.hashCode());
        }

        
        @Override
        public String toString() {
            return "StockController.MovementReq(logType="
                + this.getLogType()
                + ", materialCode="
                + this.getMaterialCode()
                + ", qty="
                + this.getQty()
                + ", bizDate="
                + this.getBizDate()
                + ", department="
                + this.getDepartment()
                + ", refWo="
                + this.getRefWo()
                + ", remark="
                + this.getRemark()
                + ", warehouseCode="
                + this.getWarehouseCode()
                + ", fromWarehouseCode="
                + this.getFromWarehouseCode()
                + ")";
        }
    }
}
