package com.dcim.finance.controller;

import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.security.RequirePerm;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/finance/cost-allocation"})
public class CostAllocationController {
    private static final Map<String, String> DIMS = Map.of(
        "department",
        "COALESCE(NULLIF(TRIM(a.department), ''), '未分配部门')",
        "region",
        "COALESCE(NULLIF(TRIM(a.region_code), ''), '未分配区域')",
        "room",
        "COALESCE(NULLIF(TRIM(a.room_code), ''), '未上架')",
        "category",
        "COALESCE(NULLIF(TRIM(a.category_code), ''), '未分类')",
        "owner",
        "COALESCE(NULLIF(TRIM(a.owner), ''), '未指定责任人')"
    );
    private final JdbcTemplate jdbc;

    @GetMapping
    @RequirePerm(
        module = "finance"
    )
    public ApiResult<Map<String, Object>> allocate(@RequestParam(required = false) String period, @RequestParam(defaultValue = "department") String dim) {
        String expr = DIMS.get(dim);
        if (expr == null) {
            throw BusinessException.badRequest("分摊维度须为 department / region / room / category / owner");
        } else {
            LocalDate p = this.resolvePeriod(period);
            List<Map<String, Object>> rows = this.jdbc
                .queryForList(
                    "SELECT "
                        + expr
                        + " AS key, COUNT(DISTINCT r.asset_no) AS \"assetCount\", COALESCE(SUM(r.month_dep), 0) AS \"monthDep\", COALESCE(SUM(r.origin_value), 0) AS \"originValue\", COALESCE(SUM(r.net_value), 0) AS \"netValue\" FROM depreciation_record r JOIN asset a ON a.asset_no = r.asset_no WHERE r.period = ? GROUP BY 1 ORDER BY \"monthDep\" DESC",
                    new Object[]{p}
                );
            BigDecimal total = rows.stream().map(r -> (BigDecimal)r.get("monthDep")).reduce(BigDecimal.ZERO, BigDecimal::add);

            for (Map<String, Object> r : rows) {
                BigDecimal dep = (BigDecimal)r.get("monthDep");
                r.put("share", total.signum() == 0 ? BigDecimal.ZERO : dep.multiply(BigDecimal.valueOf(100L)).divide(total, 2, RoundingMode.HALF_UP));
            }

            List<Map<String, Object>> trend = this.jdbc
                .queryForList(
                    "SELECT to_char(period, 'YYYY-MM') AS period, COALESCE(SUM(month_dep), 0) AS \"monthDep\", COUNT(*) AS \"assetCount\" FROM depreciation_record WHERE period > ?::date - interval '12 months' AND period <= ? GROUP BY period ORDER BY period",
                    new Object[]{p, p}
                );
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("period", YearMonth.from(p).toString());
            out.put("dim", dim);
            out.put("total", total);
            out.put("rows", rows);
            out.put("trend", trend);
            return ApiResult.ok(out);
        }
    }

    private LocalDate resolvePeriod(String period) {
        if (period != null && !period.isBlank()) {
            try {
                return YearMonth.parse(period.trim().substring(0, 7)).atDay(1);
            } catch (RuntimeException var3) {
                throw BusinessException.badRequest("期间格式须为 yyyy-MM");
            }
        } else {
            List<LocalDate> latest = this.jdbc.queryForList("SELECT MAX(period) FROM depreciation_record", LocalDate.class);
            return !latest.isEmpty() && latest.get(0) != null ? latest.get(0) : YearMonth.now().atDay(1);
        }
    }

    
    public CostAllocationController(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
}
