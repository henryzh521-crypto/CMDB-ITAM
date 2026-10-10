package com.dcim.report.controller;

import com.dcim.common.ApiResult;
import com.dcim.security.RequirePerm;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营总览扩展报表（v3.3 补齐）。
 *
 * <p>v3.2 前端运营总览调用了 6 个后端并不存在的接口（全部 404，面板永远显示“暂无数据”）。
 * 这里按系统里<b>真实存在的数据</b>实现其中 5 个；投资回报（ROI）依赖营收数据，平台不采集，故不提供，
 * 前端在无数据时隐藏该面板，而不是展示编造的数字。
 * <ul>
 *   <li>财务价值汇总：asset_finance（原值 / 累计折旧 / 净值 / 减值）按分类；</li>
 *   <li>容量规划：机柜 U 位（资产 U 高）与机柜额定功率（v_asset_power），按近 12 个月上架趋势线性外推；</li>
 *   <li>运维成本结构：工单人工 / 服务 / 其他费用 + 备件出库金额 + 月折旧 +（配置电价时）电费估算；</li>
 *   <li>TCO：购置 + 维修 +（配置电价时）能耗，按分类；</li>
 *   <li>供应商绩效：合同数 / 金额 + 所供资产近 12 个月故障率（质量合格率 = 1 - 故障率）。</li>
 * </ul>
 * 全部为聚合查询，结果缓存 5 分钟。
 */
@RestController
@RequestMapping({"/api/report"})
public class ReportExtController {
    private static final long TTL_MS = 300_000L;
    private final JdbcTemplate jdbc;
    private final Map<String, Object[]> cache = new ConcurrentHashMap<>();

    public ReportExtController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, Supplier<T> loader) {
        Object[] c = this.cache.get(key);
        long now = System.currentTimeMillis();
        if (c != null && now - (Long) c[0] < TTL_MS) {
            return (T) c[1];
        }
        T v = loader.get();
        this.cache.put(key, new Object[]{now, v});
        return v;
    }

    // ───────────────────────── 财务价值汇总 ─────────────────────────

    @GetMapping({"/asset/finance/summary"})
    @RequirePerm(module = "report")
    public ApiResult<Map<String, Object>> financeSummary() {
        return ApiResult.ok(this.cached("finance", () -> {
            List<Map<String, Object>> rows = this.jdbc.queryForList(
                "SELECT COALESCE(c.name, NULLIF(a.category_code, ''), '未分类') AS category, COUNT(*) AS count, "
                    + "COALESCE(SUM(f.origin_value), 0) AS \"originValue\", COALESCE(SUM(f.accumulated_dep), 0) AS \"accumulatedDep\", "
                    + "COALESCE(SUM(f.net_value), 0) AS \"netValue\", COALESCE(SUM(f.impairment), 0) AS impairment "
                    + "FROM asset_finance f JOIN asset a ON a.asset_no = f.asset_no AND a.deleted_at IS NULL "
                    + "LEFT JOIN category c ON c.category_code = a.category_code GROUP BY 1 ORDER BY 3 DESC"
            );
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("totalOriginValue", sum(rows, "originValue"));
            out.put("totalAccumulatedDep", sum(rows, "accumulatedDep"));
            out.put("totalNetValue", sum(rows, "netValue"));
            out.put("totalImpairment", sum(rows, "impairment"));
            out.put("byCategory", rows);
            return out;
        }));
    }

    // ───────────────────────── 容量规划 ─────────────────────────

    @GetMapping({"/capacity/planning"})
    @RequirePerm(module = "report")
    public ApiResult<Map<String, Object>> capacityPlanning() {
        return ApiResult.ok(this.cached("capacity", () -> {
            Map<String, Object> out = new LinkedHashMap<>();
            YearMonth start = YearMonth.now().minusMonths(11);
            // 近 12 个月每月新上架 U 数（按 rack_date）
            double[] monthlyU = this.monthlySeries(
                "SELECT to_char(date_trunc('month', rack_date), 'YYYY-MM') AS m, COALESCE(SUM(COALESCE(u_height, 1)), 0) AS v "
                    + "FROM asset WHERE deleted_at IS NULL AND rack_code IS NOT NULL AND rack_date >= ? GROUP BY 1",
                start
            );
            Number totalU = this.jdbc.queryForObject("SELECT COALESCE(SUM(u_capacity), 0) FROM rack", Number.class);
            Number usedU = this.jdbc.queryForObject(
                "SELECT COALESCE(SUM(COALESCE(u_height, 1)), 0) FROM asset WHERE deleted_at IS NULL AND rack_code IS NOT NULL AND unit_no IS NOT NULL",
                Number.class
            );
            if (totalU != null && totalU.doubleValue() > 0) {
                out.put("rack", forecast(usedU == null ? 0 : usedU.doubleValue(), totalU.doubleValue(), monthlyU, 85));
            }
            // 功率：资产额定/实测功率 vs 机柜额定功率上限
            Number limitKw = this.jdbc.queryForObject("SELECT COALESCE(SUM(power_limit_kw), 0) FROM rack", Number.class);
            if (limitKw != null && limitKw.doubleValue() > 0) {
                Number usedW = this.jdbc.queryForObject(
                    "SELECT COALESCE(SUM(power_w), 0) FROM v_asset_power WHERE rack_code IS NOT NULL AND status_biz <> '已报废'", Number.class
                );
                double usedKw = usedW == null ? 0 : usedW.doubleValue() / 1000.0;
                double ratio = usedU == null || usedU.doubleValue() <= 0 ? 0 : usedKw / usedU.doubleValue();
                double[] monthlyKw = new double[monthlyU.length];
                for (int i = 0; i < monthlyU.length; i++) {
                    monthlyKw[i] = monthlyU[i] * ratio;
                }
                out.put("power", forecast(usedKw, limitKw.doubleValue(), monthlyKw, 80));
            }
            // 制冷：平台不采集制冷容量，不提供（前端隐藏该序列）
            return out;
        }));
    }

    /** 线性外推未来 12 个月：月增量取近 12 个月新增量的最小二乘趋势（不低于 0） */
    private static Map<String, Object> forecast(double current, double capacity, double[] monthlyAdds, int thresholdPct) {
        int n = monthlyAdds.length;
        double sx = 0, sy = 0, sxy = 0, sxx = 0;
        for (int i = 0; i < n; i++) {
            sx += i;
            sy += monthlyAdds[i];
            sxy += i * monthlyAdds[i];
            sxx += (double) i * i;
        }
        double slope = n > 1 ? (n * sxy - sx * sy) / Math.max(1e-9, n * sxx - sx * sx) : 0;
        double intercept = n > 0 ? (sy - slope * sx) / n : 0;
        List<Map<String, Object>> fc = new ArrayList<>();
        double level = current;
        YearMonth m = YearMonth.now();
        for (int k = 1; k <= 12; k++) {
            double add = Math.max(0, intercept + slope * (n - 1 + k));
            level = Math.min(capacity * 1.2, level + add);
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("month", m.plusMonths(k).toString());
            f.put("predictedUsage", round(level));
            f.put("predictedRate", round(level / capacity * 100));
            f.put("threshold", thresholdPct);
            fc.add(f);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("currentUsage", round(current));
        out.put("totalCapacity", round(capacity));
        out.put("currentRate", round(current / capacity * 100));
        out.put("monthlyGrowth", round(Math.max(0, intercept + slope * n)));
        out.put("forecast", fc);
        return out;
    }

    // ───────────────────────── 运维成本结构 ─────────────────────────

    @GetMapping({"/opex/structure"})
    @RequirePerm(module = "report")
    public ApiResult<Map<String, Object>> opexStructure() {
        return ApiResult.ok(this.cached("opex", () -> {
            YearMonth start = YearMonth.now().minusMonths(11);
            double[] labor = this.monthlySeries(
                "SELECT to_char(date_trunc('month', finish_time), 'YYYY-MM') AS m, COALESCE(SUM(cost_labor), 0) AS v FROM workorder "
                    + "WHERE deleted_at IS NULL AND finish_time >= ? GROUP BY 1", start);
            double[] maint = this.monthlySeries(
                "SELECT to_char(date_trunc('month', finish_time), 'YYYY-MM') AS m, COALESCE(SUM(COALESCE(cost_service, 0) + COALESCE(cost_other, 0)), 0) AS v "
                    + "FROM workorder WHERE deleted_at IS NULL AND finish_time >= ? GROUP BY 1", start);
            double[] parts = this.monthlySeries(
                "SELECT to_char(date_trunc('month', l.biz_date), 'YYYY-MM') AS m, COALESCE(SUM(l.qty * COALESCE(m.price, 0)), 0) AS v "
                    + "FROM stock_log l JOIN material m ON m.material_code = l.material_code WHERE l.log_type = 'OUT' AND l.biz_date >= ? GROUP BY 1", start);
            double[] dep = this.monthlySeries(
                "SELECT to_char(period, 'YYYY-MM') AS m, COALESCE(SUM(month_dep), 0) AS v FROM depreciation_record WHERE period >= ? GROUP BY 1", start);
            double price = this.electricityPrice();
            double[] power = new double[12];
            if (price > 0) {
                Number w = this.jdbc.queryForObject(
                    "SELECT COALESCE(SUM(power_w), 0) FROM v_asset_power WHERE rack_code IS NOT NULL AND status_biz IN ('在用(计费)', '维修中')", Number.class);
                double kw = w == null ? 0 : w.doubleValue() / 1000.0;
                for (int i = 0; i < 12; i++) {
                    power[i] = kw * 24 * start.plusMonths(i).lengthOfMonth() * price;
                }
            }
            List<Map<String, Object>> trend = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("month", start.plusMonths(i).toString());
                r.put("power", round(power[i]));
                r.put("maintenance", round(maint[i]));
                r.put("labor", round(labor[i]));
                r.put("parts", round(parts[i]));
                r.put("depreciation", round(dep[i]));
                trend.add(r);
            }
            Map<String, Double> totals = new LinkedHashMap<>();
            totals.put(price > 0 ? "电费（估算）" : "电费（未配置电价）", total(power));
            totals.put("维修服务", total(maint));
            totals.put("人工", total(labor));
            totals.put("备件", total(parts));
            totals.put("折旧", total(dep));
            double all = totals.values().stream().mapToDouble(Double::doubleValue).sum();
            List<Map<String, Object>> breakdown = new ArrayList<>();
            totals.forEach((k, v) -> {
                if (v > 0) {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("name", k);
                    b.put("value", round(v));
                    b.put("percent", all > 0 ? round(v / all) : 0);
                    breakdown.add(b);
                }
            });
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("total", round(all));
            out.put("breakdown", breakdown);
            out.put("monthlyTrend", trend);
            out.put("electricityPriceConfigured", price > 0);
            return out;
        }));
    }

    // ───────────────────────── TCO ─────────────────────────

    @GetMapping({"/asset/tco"})
    @RequirePerm(module = "report")
    public ApiResult<Map<String, Object>> tco() {
        return ApiResult.ok(this.cached("tco", () -> {
            double price = this.electricityPrice();
            List<Map<String, Object>> rows = this.jdbc.queryForList(
                "WITH a AS (SELECT a.asset_no, COALESCE(c.name, NULLIF(a.category_code, ''), '未分类') AS category, "
                    + "  COALESCE(f.origin_value, a.purchase_price, 0) AS acq, COALESCE(f.accumulated_dep, 0) AS dep, "
                    + "  GREATEST(EXTRACT(EPOCH FROM (NOW() - COALESCE(a.rack_date, a.purchase_date, a.created_at::date)::timestamp)) / 31557600.0, 0.0833) AS years "
                    + "  FROM asset a LEFT JOIN asset_finance f ON f.asset_no = a.asset_no LEFT JOIN category c ON c.category_code = a.category_code "
                    + "  WHERE a.deleted_at IS NULL), "
                    + "wc AS (SELECT i.asset_no, SUM((COALESCE(w.cost_labor, 0) + COALESCE(w.cost_service, 0) + COALESCE(w.cost_other, 0)) / GREATEST(cnt.n, 1)) AS cost "
                    + "  FROM workorder w JOIN workorder_item i ON i.wo_no = w.wo_no "
                    + "  JOIN (SELECT wo_no, COUNT(*) AS n FROM workorder_item GROUP BY wo_no) cnt ON cnt.wo_no = w.wo_no "
                    + "  WHERE w.deleted_at IS NULL GROUP BY i.asset_no), "
                    + "pw AS (SELECT asset_no, power_w FROM v_asset_power) "
                    + "SELECT a.category, COUNT(*) AS assets, SUM(a.acq) AS acq, SUM(COALESCE(wc.cost, 0)) AS maint, SUM(a.dep) AS dep, "
                    + "  SUM(COALESCE(pw.power_w, 0) / 1000.0 * 8766 * a.years) AS kwh, AVG(a.years) AS years "
                    + "FROM a LEFT JOIN wc ON wc.asset_no = a.asset_no LEFT JOIN pw ON pw.asset_no = a.asset_no "
                    + "GROUP BY a.category ORDER BY acq DESC NULLS LAST"
            );
            List<Map<String, Object>> by = new ArrayList<>();
            double tot = 0, annual = 0;
            for (Map<String, Object> r : rows) {
                double acq = num(r.get("acq")), maint = num(r.get("maint")), dep = num(r.get("dep"));
                double energy = price > 0 ? num(r.get("kwh")) * price : 0;
                double years = Math.max(1.0, num(r.get("years")));
                double t = acq + maint + energy;
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("category", r.get("category"));
                o.put("assets", r.get("assets"));
                o.put("acquisitionCost", round(acq));
                o.put("maintenanceCost", round(maint));
                o.put("energyCost", round(energy));
                o.put("depreciation", round(dep));
                o.put("disposalCost", 0);
                o.put("totalTco", round(t));
                o.put("avgAnnualTco", round(t / years));
                by.add(o);
                tot += t;
                annual += t / years;
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("totalTco", round(tot));
            summary.put("avgAnnualTco", round(annual));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("byCategory", by);
            out.put("summary", summary);
            out.put("electricityPriceConfigured", price > 0);
            return out;
        }));
    }

    // ───────────────────────── 供应商绩效 ─────────────────────────

    @GetMapping({"/supplier/performance"})
    @RequirePerm(module = "report")
    public ApiResult<Map<String, Object>> supplierPerformance() {
        return ApiResult.ok(this.cached("supplier", () -> {
            List<Map<String, Object>> rows = this.jdbc.queryForList(
                "WITH sc AS (SELECT s.supplier_code, s.name, c.contract_no, c.amount FROM supplier s JOIN contract c ON c.party = s.name), "
                    + "agg AS (SELECT supplier_code, name, COUNT(*) AS contracts, COALESCE(SUM(amount), 0) AS amount FROM sc GROUP BY 1, 2), "
                    + "sa AS (SELECT DISTINCT sc.supplier_code, ca.asset_no FROM sc JOIN contract_asset ca ON ca.contract_no = sc.contract_no), "
                    + "fa AS (SELECT DISTINCT i.asset_no FROM workorder_item i JOIN workorder w ON w.wo_no = i.wo_no "
                    + "       WHERE w.wo_type IN ('故障维修', '部件更换') AND w.deleted_at IS NULL AND w.apply_time >= NOW() - INTERVAL '12 months') "
                    + "SELECT agg.supplier_code AS \"supplierId\", agg.name AS \"supplierName\", agg.contracts AS \"totalContracts\", "
                    + "  agg.amount AS \"totalAmount\", COUNT(sa.asset_no) AS assets, COUNT(fa.asset_no) AS \"faultAssets\" "
                    + "FROM agg LEFT JOIN sa ON sa.supplier_code = agg.supplier_code LEFT JOIN fa ON fa.asset_no = sa.asset_no "
                    + "GROUP BY 1, 2, 3, 4 ORDER BY 4 DESC LIMIT 50"
            );
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                long assets = (long) num(r.get("assets"));
                long fault = (long) num(r.get("faultAssets"));
                Double quality = assets > 0 ? round(1.0 - (double) fault / assets) : null;
                Map<String, Object> o = new LinkedHashMap<>(r);
                o.put("qualityPassRate", quality);
                // 交付及时率 / 响应时长：平台未记录供应商交付与响应时间，返回 null（前端不绘制该维度）
                o.put("deliveryOnTimeRate", null);
                o.put("avgResponseHours", null);
                o.put("score", quality == null ? null : round(quality * 100));
                out.add(o);
            }
            return Map.of("suppliers", out);
        }));
    }

    // ───────────────────────── 工具 ─────────────────────────

    private double electricityPrice() {
        try {
            List<String> v = this.jdbc.queryForList(
                "SELECT setting_value FROM capacity_setting WHERE setting_key = 'electricity_price'", String.class);
            return v.isEmpty() || v.get(0) == null || v.get(0).isBlank() ? 0 : Double.parseDouble(v.get(0).trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** 取近 12 个月（自 start 起）按月汇总的数值序列，缺失月份补 0 */
    private double[] monthlySeries(String sql, YearMonth start) {
        double[] out = new double[12];
        LocalDate from = start.atDay(1);
        for (Map<String, Object> r : this.jdbc.queryForList(sql, from)) {
            Object m = r.get("m");
            if (m == null) {
                continue;
            }
            int idx = (int) java.time.temporal.ChronoUnit.MONTHS.between(start, YearMonth.parse(String.valueOf(m)));
            if (idx >= 0 && idx < 12) {
                out[idx] += num(r.get("v"));
            }
        }
        return out;
    }

    private static double total(double[] a) {
        double s = 0;
        for (double v : a) {
            s += v;
        }
        return s;
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static double sum(List<Map<String, Object>> rows, String k) {
        return round(rows.stream().mapToDouble(r -> num(r.get(k))).sum());
    }

    private static double round(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return 0;
        }
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
