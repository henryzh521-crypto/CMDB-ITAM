package com.dcim.report.controller;

import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.report.dto.GroupCount;
import com.dcim.report.dto.GroupCountValue;
import com.dcim.report.dto.MaterialStockRow;
import com.dcim.report.dto.RackUsageRow;
import com.dcim.report.dto.RoomUsageRow;
import com.dcim.report.dto.SlaRow;
import com.dcim.report.dto.WarehouseStockAggRow;
import com.dcim.report.dto.WoDurationRow;
import com.dcim.report.mapper.ReportQueryMapper;
import com.dcim.security.RequirePerm;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/report"})
public class ReportController {
    private final DashboardExtras extras;
    private static final Set<String> ASSET_DIMS = Set.of("statusBiz", "regionCode", "categoryCode", "ownerType");
    private static final Set<String> WO_DIMS = Set.of("status", "woType", "priority", "rootCause");
    private static final Set<String> TREND_GRANULARITIES = Set.of("day", "week", "month");
    private static final int DEFAULT_RANGE_DAYS = 89;
    private static final int TOP_LIMIT = 20;
    private static final long TREND_MONTHS = 12L;
    private final ReportQueryMapper reportMapper;

    public ReportController(ReportQueryMapper reportMapper,
        final DashboardExtras extras) {
        this.extras = extras;
        this.reportMapper = reportMapper;
    }

    @GetMapping({"/dashboard"})
    @RequirePerm(
        module = "report"
    )
    public ApiResult<Map<String, Object>> dashboard() {
        // 全局聚合与用户无关：缓存 30 秒（10 万资产时 11 条聚合约 200ms，首页人人都会打开）
        Object[] c = this.dashboardCache;
        long now = System.currentTimeMillis();
        if (c != null && now - (Long) c[0] < 30000L) {
            return ApiResult.ok((Map<String, Object>) c[1]);
        }
        Map<String, Object> out = this.buildDashboard();
        this.dashboardCache = new Object[]{now, out};
        return ApiResult.ok(out);
    }

    private volatile Object[] dashboardCache;

    private Map<String, Object> buildDashboard() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("assetTotal", this.reportMapper.countAsset());
        out.put("byStatusBiz", this.withLabel(this.reportMapper.groupAssetByStatusBiz()));
        out.put("byRegion", this.withLabel(this.reportMapper.groupAssetByRegion()));
        out.put("byCategory", this.withLabel(this.reportMapper.groupAssetByCategory()));
        out.put("rackUsage", this.rackUsage(this.reportMapper.selectRackUsage()));
        out.put("woStats", this.reportMapper.selectWoStats());
        out.put("categoryStatus", this.reportMapper.selectCategoryStatusCounts());
        out.put("byRoomUsage", this.roomUsage(this.reportMapper.selectRoomUsage()));
        out.put("byEventType", this.withLabel(this.reportMapper.selectEventTypeCounts()));
        LocalDate from = LocalDate.now().withDayOfMonth(1).minusMonths(11L);
        List<String> months = this.monthKeys(from);
        out.put("trendRacked", this.fillMonths(this.reportMapper.selectRackTrend(from), months));
        out.put("trendPurchased", this.fillMonths(this.reportMapper.selectPurchaseTrend(from), months));
        out.put("recent30dRacked", this.reportMapper.countRecentRacked());
        this.extras.fill(out);
        out.put("generatedAt", java.time.LocalDateTime.now().withNano(0).toString());
        return java.util.Collections.unmodifiableMap(out);
    }

    @GetMapping({"/assets"})
    @RequirePerm(
        module = "report"
    )
    public ApiResult<List<GroupCountValue>> assets(@RequestParam String groupBy) {
        if (groupBy != null && ASSET_DIMS.contains(groupBy)) {
            List<GroupCountValue> rows = this.reportMapper.groupAssetByDim(groupBy);
            rows.forEach(r -> r.setLabel(r.getKey()));
            return ApiResult.ok(rows);
        } else {
            throw BusinessException.badRequest("非法的分组维度：" + groupBy + "，仅支持 statusBiz/regionCode/categoryCode/ownerType");
        }
    }

    @GetMapping({"/workorders"})
    @RequirePerm(
        module = "report"
    )
    public ApiResult<List<GroupCount>> workorders(@RequestParam String groupBy) {
        if (groupBy != null && WO_DIMS.contains(groupBy)) {
            List<GroupCount> rows = this.reportMapper.groupWoByDim(groupBy);
            rows.forEach(r -> r.setLabel(r.getKey()));
            return ApiResult.ok(rows);
        } else {
            throw BusinessException.badRequest("非法的分组维度：" + groupBy + "，仅支持 status/woType/priority/rootCause");
        }
    }

    @GetMapping({"/workorders/sla"})
    @RequirePerm(
        module = "report"
    )
    public ApiResult<Map<String, Object>> workorderSla() {
        SlaRow s = this.reportMapper.selectSla();
        long total = s.getTotal() == null ? 0L : s.getTotal();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("respondedCount", s.getResponded());
        out.put("responseRate", this.rate(s.getResponded(), total));
        out.put("overdueCount", s.getOverdue());
        out.put("complianceRate", total == 0L ? null : this.rate(total - (s.getOverdue() == null ? 0L : s.getOverdue()), total));
        return ApiResult.ok(out);
    }

    @GetMapping({"/workorders/repair/duration"})
    @RequirePerm(
        module = "report"
    )
    public ApiResult<Map<String, Object>> repairDuration(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        ReportController.Range range = this.range(from, to);
        WoDurationRow d = this.reportMapper.selectRepairDuration(range.from(), range.to());
        long done = this.num(d.getDoneCount());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", range.from());
        out.put("to", range.to());
        out.put("woCount", this.num(d.getWoCount()));
        out.put("doneCount", done);
        out.put("rootCauseCount", this.num(d.getRootCauseCount()));
        out.put("rootCauseFillRate", this.rate(d.getRootCauseCount(), done));
        out.put("sampleCount", this.num(d.getSampleCount()));
        out.put("avgHours", d.getAvgHours());
        out.put("medianHours", d.getMedianHours());
        out.put("p90Hours", d.getP90Hours());
        out.put("minHours", d.getMinHours());
        out.put("maxHours", d.getMaxHours());
        out.put("repairSampleCount", this.num(d.getRepairSampleCount()));
        out.put("avgRepairHours", d.getAvgRepairHours());
        out.put("medianRepairHours", d.getMedianRepairHours());
        out.put("responseSampleCount", this.num(d.getResponseSampleCount()));
        out.put("avgResponseHours", d.getAvgResponseHours());
        out.put("medianResponseHours", d.getMedianResponseHours());
        out.put("respondRate", this.rate(d.getResponseSampleCount(), d.getSampleCount()));
        out.put("rootCauses", this.withLabel(this.reportMapper.selectRepairRootCauses(range.from(), range.to())));
        return ApiResult.ok(out);
    }

    @GetMapping({"/workorders/repair/trend"})
    @RequirePerm(
        module = "report"
    )
    public ApiResult<List<?>> repairTrend(
        @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(defaultValue = "month") String granularity
    ) {
        if (!TREND_GRANULARITIES.contains(granularity)) {
            throw BusinessException.badRequest("非法的趋势粒度：" + granularity + "，仅支持 day/week/month");
        } else {
            String[] parts = switch (granularity) {
                case "day" -> new String[]{"day", "1 day", "YYYY-MM-DD"};
                case "week" -> new String[]{"week", "7 days", "IYYY-\"W\"IW"};
                default -> new String[]{"month", "1 month", "YYYY-MM"};
            };
            ReportController.Range range = this.range(from, to);
            return ApiResult.ok(this.reportMapper.selectRepairTrend(range.from(), range.to(), parts[0], parts[1], parts[2]));
        }
    }

    @GetMapping({"/workorders/repair/materials"})
    @RequirePerm(
        module = "report"
    )
    public ApiResult<Map<String, Object>> repairMaterials(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        ReportController.Range range = this.range(from, to);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", range.from());
        out.put("to", range.to());
        out.put("summary", this.reportMapper.selectWoConsumptionSummary(range.from(), range.to()));
        out.put("byWo", this.reportMapper.selectWoConsumptionTop(range.from(), range.to(), 20));
        out.put("byMaterial", this.reportMapper.selectMaterialConsumptionTop(range.from(), range.to(), 20));
        return ApiResult.ok(out);
    }

    @GetMapping({"/workorders/repair/cost"})
    @RequirePerm(
        module = "report"
    )
    public ApiResult<Map<String, Object>> repairCost(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        ReportController.Range range = this.range(from, to);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", range.from());
        out.put("to", range.to());
        out.put("summary", this.reportMapper.selectRepairCostSummary(range.from(), range.to()));
        out.put("byWo", this.reportMapper.selectRepairCostTop(range.from(), range.to(), 20));
        return ApiResult.ok(out);
    }

    @GetMapping({"/warehouse"})
    @RequirePerm(
        module = "report"
    )
    public ApiResult<Map<String, Object>> warehouse(@RequestParam(required = false) String warehouseCode) {
        String whFilter = warehouseCode != null && !warehouseCode.isBlank() ? warehouseCode.trim() : null;
        List<MaterialStockRow> items = whFilter == null ? this.reportMapper.selectMaterials() : this.reportMapper.selectMaterialsInWarehouse(whFilter);
        BigDecimal totalValue = BigDecimal.ZERO;
        long low = 0L;
        long out = 0L;
        List<Map<String, Object>> rows = new ArrayList<>();

        for (MaterialStockRow m : items) {
            BigDecimal price = m.getPrice() == null ? BigDecimal.ZERO : m.getPrice();
            int stock = m.getStock() == null ? 0 : m.getStock();
            BigDecimal stockValue = price.multiply(BigDecimal.valueOf((long)stock));
            m.setStockValue(stockValue);
            totalValue = totalValue.add(stockValue);
            String alert = null;
            if (stock == 0) {
                alert = "OUT";
                out++;
            } else if (m.getSafetyStock() != null && stock <= m.getSafetyStock()) {
                alert = "LOW";
                low++;
            }

            m.setAlert(alert);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialCode", m.getMaterialCode());
            row.put("name", m.getName());
            row.put("category", m.getCategory());
            row.put("unit", m.getUnit());
            row.put("stock", m.getStock());
            row.put("safetyStock", m.getSafetyStock());
            row.put("maxStock", m.getMaxStock());
            row.put("totalIn", m.getTotalIn());
            row.put("totalOut", m.getTotalOut());
            row.put("stockValue", m.getStockValue());
            row.put("alert", m.getAlert());
            row.put("warehouseCode", m.getWarehouseCode());
            rows.add(row);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("materialCount", items.size());
        summary.put("totalStockValue", totalValue);
        summary.put("lowStockCount", low);
        summary.put("outOfStockCount", out);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", rows);
        data.put("summary", summary);
        data.put("byWarehouse", this.warehouseAgg());
        return ApiResult.ok(data);
    }

    private List<Map<String, Object>> warehouseAgg() {
        List<Map<String, Object>> out = new ArrayList<>();

        for (WarehouseStockAggRow w : this.reportMapper.selectWarehouseStockAgg()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("warehouseCode", w.getWarehouseCode());
            row.put("name", w.getName());
            row.put("warehouseType", w.getWarehouseType());
            row.put("status", w.getStatus());
            row.put("materialCount", w.getMaterialCount());
            row.put("stockQty", w.getStockQty());
            row.put("stockValue", w.getStockValue());
            row.put("lowCount", w.getLowCount());
            row.put("outCount", w.getOutCount());
            row.put("empty", w.getStockQty() == null || w.getStockQty() == 0);
            out.add(row);
        }

        return out;
    }

    private ReportController.Range range(String from, String to) {
        LocalDate today = LocalDate.now();
        LocalDate f = from != null && !from.isBlank() ? this.parseDay("from", from) : today.minusDays(89L);
        LocalDate t = to != null && !to.isBlank() ? this.parseDay("to", to) : today;
        if (f.isAfter(t)) {
            throw BusinessException.badRequest("起始日期不能晚于结束日期：" + f + " > " + t);
        } else if (f.isBefore(t.minusYears(3L))) {
            throw BusinessException.badRequest("报表时间跨度不能超过 3 年：" + f + " ~ " + t);
        } else {
            return new ReportController.Range(f.toString(), t.toString());
        }
    }

    private LocalDate parseDay(String name, String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException var4) {
            throw BusinessException.badRequest(name + " 须为 yyyy-MM-dd 格式的日期：" + value);
        }
    }

    private long num(Long v) {
        return v == null ? 0L : v;
    }

    private List<GroupCount> withLabel(List<GroupCount> rows) {
        rows.forEach(r -> r.setLabel(r.getKey()));
        return rows;
    }

    private Map<String, Object> rackUsage(RackUsageRow r) {
        long total = r.getTotalUnits() == null ? 0L : r.getTotalUnits();
        long used = r.getUsedUnits() == null ? 0L : r.getUsedUnits();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalUnits", total);
        out.put("usedUnits", used);
        out.put("usageRate", this.rate(used, total));
        return out;
    }

    private List<String> monthKeys(LocalDate start) {
        List<String> keys = new ArrayList<>();
        LocalDate end = LocalDate.now().withDayOfMonth(1);

        for (LocalDate m = start; !m.isAfter(end); m = m.plusMonths(1L)) {
            keys.add(String.format("%04d-%02d", m.getYear(), m.getMonthValue()));
        }

        return keys;
    }

    private List<GroupCount> fillMonths(List<GroupCount> rows, List<String> keys) {
        Map<String, Long> hit = new LinkedHashMap<>();

        for (GroupCount r : rows) {
            hit.merge(r.getKey(), r.getCount() == null ? 0L : r.getCount(), Long::sum);
        }

        List<GroupCount> out = new ArrayList<>(keys.size());

        for (String k : keys) {
            GroupCount g = new GroupCount();
            g.setKey(k);
            g.setLabel(k);
            g.setCount(hit.getOrDefault(k, 0L));
            out.add(g);
        }

        return out;
    }

    private List<Map<String, Object>> roomUsage(List<RoomUsageRow> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());

        for (RoomUsageRow r : rows) {
            long cap = r.getUCap() == null ? 0L : r.getUCap();
            long occ = r.getUOcc() == null ? 0L : r.getUOcc();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", r.getKey());
            m.put("label", r.getLabel() != null && !r.getLabel().isBlank() ? r.getLabel() : r.getKey());
            m.put("uCap", cap);
            m.put("uOcc", occ);
            m.put("rate", this.rate(occ, cap));
            out.add(m);
        }

        return out;
    }

    private BigDecimal rate(Number numerator, long denominator) {
        return denominator == 0L
            ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(numerator == null ? 0.0 : numerator.doubleValue()).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private static record Range(String from, String to) {
    }
}
