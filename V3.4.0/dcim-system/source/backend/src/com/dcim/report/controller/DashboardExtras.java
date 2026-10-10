package com.dcim.report.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 运营总览补充字段（v3.3）：前端「资产到期/续费预警」「机柜 U 位容量预测」两个面板读取
 * expiryCount / expiry30d / expiryMonthly / forecast / forecastMonths，v3.2 后端从未返回，面板恒为空。
 */
@Component
public class DashboardExtras {
    private static final String ACTIVE = "a.deleted_at IS NULL AND a.status_biz NOT IN ('已报废', '已退役', '已退租')";
    private final JdbcTemplate jdbc;

    public DashboardExtras(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void fill(Map<String, Object> out) {
        LocalDate today = LocalDate.now();
        out.put("expiryCount", this.count("SELECT COUNT(*) FROM asset a WHERE " + ACTIVE + " AND a.warranty_end < ?", today));
        out.put("expiry30d", this.count("SELECT COUNT(*) FROM asset a WHERE " + ACTIVE + " AND a.warranty_end BETWEEN ? AND ?", today, today.plusDays(30)));
        // 未来 12 个月保修到期分布（台数 + 原值）
        YearMonth start = YearMonth.now();
        Map<String, Map<String, Object>> months = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", start.plusMonths(i).toString());
            m.put("count", 0L);
            m.put("originValue", 0.0);
            months.put(start.plusMonths(i).toString(), m);
        }
        for (Map<String, Object> r : this.jdbc.queryForList(
            "SELECT to_char(date_trunc('month', a.warranty_end), 'YYYY-MM') AS m, COUNT(*) AS c, "
                + "COALESCE(SUM(COALESCE(f.origin_value, a.purchase_price, 0)), 0) AS v "
                + "FROM asset a LEFT JOIN asset_finance f ON f.asset_no = a.asset_no WHERE " + ACTIVE
                + " AND a.warranty_end >= ? AND a.warranty_end < ? GROUP BY 1",
            start.atDay(1), start.plusMonths(12).atDay(1))) {
            Map<String, Object> m = months.get(String.valueOf(r.get("m")));
            if (m != null) {
                m.put("count", ((Number) r.get("c")).longValue());
                m.put("originValue", ((Number) r.get("v")).doubleValue());
            }
        }
        out.put("expiryMonthly", new ArrayList<>(months.values()));
        this.fillForecast(out);
    }

    /** 过去 6 个月实际 U 位占用率（按上架日期回推）+ 未来 6 个月线性预测；forecastMonths = 预计达到 85% 的月数 */
    private void fillForecast(Map<String, Object> out) {
        Number total = this.jdbc.queryForObject("SELECT COALESCE(SUM(u_capacity), 0) FROM rack", Number.class);
        if (total == null || total.doubleValue() <= 0) {
            return;
        }
        double cap = total.doubleValue();
        Number used = this.jdbc.queryForObject(
            "SELECT COALESCE(SUM(COALESCE(u_height, 1)), 0) FROM asset WHERE deleted_at IS NULL AND rack_code IS NOT NULL AND unit_no IS NOT NULL", Number.class);
        double now = used == null ? 0 : used.doubleValue();
        YearMonth cur = YearMonth.now();
        double[] adds = new double[6];
        for (Map<String, Object> r : this.jdbc.queryForList(
            "SELECT to_char(date_trunc('month', rack_date), 'YYYY-MM') AS m, COALESCE(SUM(COALESCE(u_height, 1)), 0) AS v FROM asset "
                + "WHERE deleted_at IS NULL AND rack_code IS NOT NULL AND rack_date >= ? GROUP BY 1",
            cur.minusMonths(5).atDay(1))) {
            int idx = (int) java.time.temporal.ChronoUnit.MONTHS.between(cur.minusMonths(5), YearMonth.parse(String.valueOf(r.get("m"))));
            if (idx >= 0 && idx < 6) {
                adds[idx] += ((Number) r.get("v")).doubleValue();
            }
        }
        double avg = 0;
        for (double a : adds) {
            avg += a;
        }
        avg /= 6.0;
        List<Map<String, Object>> fc = new ArrayList<>();
        // 回推历史：当月末占用 = 当前 - 之后各月新增
        double level = now;
        double[] hist = new double[6];
        for (int i = 5; i >= 0; i--) {
            hist[i] = level;
            level -= adds[i];
        }
        for (int i = 0; i < 6; i++) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("month", cur.minusMonths(5 - i).toString());
            f.put("actualRate", round(hist[i] / cap * 100));
            f.put("predictedRate", null);
            fc.add(f);
        }
        double p = now;
        Integer reach = p / cap >= 0.85 ? 0 : null;
        for (int k = 1; k <= 6; k++) {
            p += avg;
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("month", cur.plusMonths(k).toString());
            f.put("actualRate", null);
            f.put("predictedRate", round(Math.min(120, p / cap * 100)));
            fc.add(f);
        }
        if (reach == null && avg > 0) {
            reach = (int) Math.ceil((cap * 0.85 - now) / avg);
        }
        // 预测窗口仅 6 个月：reach 超窗时收敛到 6，与前端「forecastMonths ?? 6」的展示口径一致
        if (reach != null && reach > 6) {
            reach = 6;
        }
        out.put("forecast", fc);
        out.put("forecastMonths", reach);
    }

    private long count(String sql, Object... args) {
        Number n = this.jdbc.queryForObject(sql, Number.class, args);
        return n == null ? 0L : n.longValue();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
