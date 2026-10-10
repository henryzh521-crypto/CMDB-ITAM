package com.dcim.warehouse.controller;

import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.security.RequirePerm;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/stock-levels"})
public class StockLevelController {
    private static final String LEVEL_EXPR = "CASE WHEN m.stock <= 0 AND m.safety_stock > 0 THEN 'OUT'\n     WHEN m.safety_stock > 0 AND m.stock < m.safety_stock THEN 'LOW'\n     WHEN m.max_stock > 0 AND m.stock > m.max_stock THEN 'OVER'\n     WHEN m.safety_stock = 0 AND m.max_stock = 0 THEN 'UNSET'\n     ELSE 'NORMAL' END";
    private final JdbcTemplate jdbc;

    @GetMapping
    @RequirePerm(
        module = "materials"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String level,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String warehouseCode
    ) {
        long p = Math.max(1L, page);
        long s = Math.max(1L, Math.min(size, 200L));
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.append(" AND (m.material_code ILIKE ? OR m.name ILIKE ? OR m.model ILIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
        }

        if (category != null && !category.isBlank()) {
            where.append(" AND m.category = ?");
            args.add(category.trim());
        }

        if (warehouseCode != null && !warehouseCode.isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM stock_level sl WHERE sl.material_code = m.material_code AND sl.warehouse_code = ?)");
            args.add(warehouseCode.trim());
        }

        if (level != null && !level.isBlank()) {
            String lv = level.trim().toUpperCase();
            if (!List.of("OUT", "LOW", "OVER", "NORMAL", "UNSET", "ALERT").contains(lv)) {
                throw BusinessException.badRequest("水位等级参数非法：" + level);
            }

            if ("ALERT".equals(lv)) {
                where.append(" AND (")
                    .append(
                        "CASE WHEN m.stock <= 0 AND m.safety_stock > 0 THEN 'OUT'\n     WHEN m.safety_stock > 0 AND m.stock < m.safety_stock THEN 'LOW'\n     WHEN m.max_stock > 0 AND m.stock > m.max_stock THEN 'OVER'\n     WHEN m.safety_stock = 0 AND m.max_stock = 0 THEN 'UNSET'\n     ELSE 'NORMAL' END"
                    )
                    .append(") IN ('OUT','LOW')");
            } else {
                where.append(" AND (")
                    .append(
                        "CASE WHEN m.stock <= 0 AND m.safety_stock > 0 THEN 'OUT'\n     WHEN m.safety_stock > 0 AND m.stock < m.safety_stock THEN 'LOW'\n     WHEN m.max_stock > 0 AND m.stock > m.max_stock THEN 'OVER'\n     WHEN m.safety_stock = 0 AND m.max_stock = 0 THEN 'UNSET'\n     ELSE 'NORMAL' END"
                    )
                    .append(") = ?");
                args.add(lv);
            }
        }

        Long total = (Long)this.jdbc.queryForObject("SELECT COUNT(*) FROM material m" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(s);
        pageArgs.add((p - 1L) * s);
        List<Map<String, Object>> rows = this.jdbc
            .queryForList(
                "SELECT m.material_code AS \"materialCode\", m.name, m.category, m.model, m.unit,\n       m.stock, m.safety_stock AS \"safetyStock\", m.max_stock AS \"maxStock\", m.price,\nCASE WHEN m.stock <= 0 AND m.safety_stock > 0 THEN 'OUT'\n     WHEN m.safety_stock > 0 AND m.stock < m.safety_stock THEN 'LOW'\n     WHEN m.max_stock > 0 AND m.stock > m.max_stock THEN 'OVER'\n     WHEN m.safety_stock = 0 AND m.max_stock = 0 THEN 'UNSET'\n     ELSE 'NORMAL' END        AS level,\n       COALESCE(c.out30, 0) AS \"out30\"\nFROM material m\nLEFT JOIN (SELECT material_code, SUM(qty) AS out30 FROM stock_log\n           WHERE log_type = 'OUT' AND biz_date >= CURRENT_DATE - 30\n           GROUP BY material_code) c ON c.material_code = m.material_code\n"
                    + where
                    // v3.2 缺少空白：无筛选条件时拼成 "WHERE 1=1ORDER BY"，PostgreSQL 15+ 报语法错误 → 库存水位页 500
                    + "\nORDER BY CASE (CASE WHEN m.stock <= 0 AND m.safety_stock > 0 THEN 'OUT'\n     WHEN m.safety_stock > 0 AND m.stock < m.safety_stock THEN 'LOW'\n     WHEN m.max_stock > 0 AND m.stock > m.max_stock THEN 'OVER'\n     WHEN m.safety_stock = 0 AND m.max_stock = 0 THEN 'UNSET'\n     ELSE 'NORMAL' END) WHEN 'OUT' THEN 0 WHEN 'LOW' THEN 1 WHEN 'OVER' THEN 2 ELSE 3 END, m.material_code\nLIMIT ? OFFSET ?\n",
                pageArgs.toArray()
            );
        Map<String, List<Map<String, Object>>> byMat = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            List<Object> codes = rows.stream().map(rx -> rx.get("materialCode")).map(Object.class::cast).toList();
            String in = String.join(",", Collections.nCopies(codes.size(), "?"));

            for (Map<String, Object> w : this.jdbc
                .queryForList(
                    "SELECT sl.material_code, sl.warehouse_code AS \"warehouseCode\", w.name AS \"warehouseName\", sl.qty FROM stock_level sl LEFT JOIN warehouse w ON w.warehouse_code = sl.warehouse_code WHERE sl.material_code IN ("
                        + in
                        + ") AND sl.qty <> 0 ORDER BY sl.warehouse_code",
                    codes.toArray()
                )) {
                String code = String.valueOf(w.remove("material_code"));
                byMat.computeIfAbsent(code, k -> new ArrayList<>()).add(w);
            }
        }

        for (Map<String, Object> r : rows) {
            int stock = ((Number)r.get("stock")).intValue();
            int safety = ((Number)r.get("safetyStock")).intValue();
            int max = ((Number)r.get("maxStock")).intValue();
            long out30 = ((Number)r.get("out30")).longValue();
            BigDecimal daily = BigDecimal.valueOf(out30).divide(BigDecimal.valueOf(30L), 2, RoundingMode.HALF_UP);
            r.put("dailyOut", daily);
            r.put("daysOfCover", out30 == 0L ? null : BigDecimal.valueOf((long)stock).divide(daily.max(new BigDecimal("0.01")), 0, RoundingMode.DOWN));
            int target = max > 0 ? max : safety * 2;
            r.put("suggestQty", !"OUT".equals(r.get("level")) && !"LOW".equals(r.get("level")) ? 0 : Math.max(0, target - stock));
            r.put("warehouses", byMat.getOrDefault(String.valueOf(r.get("materialCode")), List.of()));
        }

        return ApiResult.ok(new PageResult<>(total == null ? 0L : total, rows));
    }

    @GetMapping({"/summary"})
    @RequirePerm(
        module = "materials"
    )
    public ApiResult<Map<String, Object>> summary() {
        Map<String, Object> out = new LinkedHashMap<>();

        for (String lv : List.of("OUT", "LOW", "OVER", "NORMAL", "UNSET")) {
            out.put(lv, 0L);
        }

        this.jdbc
            .queryForList(
                "SELECT (CASE WHEN m.stock <= 0 AND m.safety_stock > 0 THEN 'OUT'\n     WHEN m.safety_stock > 0 AND m.stock < m.safety_stock THEN 'LOW'\n     WHEN m.max_stock > 0 AND m.stock > m.max_stock THEN 'OVER'\n     WHEN m.safety_stock = 0 AND m.max_stock = 0 THEN 'UNSET'\n     ELSE 'NORMAL' END) AS level, COUNT(*) AS n FROM material m GROUP BY 1"
            )
            .forEach(r -> out.put(String.valueOf(r.get("level")), ((Number)r.get("n")).longValue()));
        out.put("stockValue", this.jdbc.queryForObject("SELECT COALESCE(SUM(stock * COALESCE(price, 0)), 0) FROM material", BigDecimal.class));
        return ApiResult.ok(out);
    }

    
    public StockLevelController(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
}
