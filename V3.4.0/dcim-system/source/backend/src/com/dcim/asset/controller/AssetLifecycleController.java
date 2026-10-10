package com.dcim.asset.controller;

import com.dcim.common.ApiResult;
import com.dcim.common.PageResult;
import com.dcim.security.RequirePerm;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/asset-lifecycle"})
public class AssetLifecycleController {
    private final JdbcTemplate jdbc;
    private final com.dcim.security.DataScopeService dataScope;

    @GetMapping({"/activities"})
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<PageResult<Map<String, Object>>> activities(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) String operator,
        @RequestParam(defaultValue = "30") int days
    ) {
        long s = Math.max(1L, Math.min(size, 100L));
        long p = Math.max(1L, page);
        StringBuilder where = new StringBuilder(" WHERE e.event_time >= NOW() - make_interval(days => ?)");
        List<Object> args = new ArrayList<>();
        args.add(Math.max(1, Math.min(days, 366)));
        if (eventType != null && !eventType.isBlank()) {
            where.append(" AND e.event_type = ?");
            args.add(eventType.trim());
        }

        if (operator != null && !operator.isBlank()) {
            where.append(" AND e.operator = ?");
            args.add(operator.trim());
        }

        // v3.5：注入数据范围（SELF/DEPT），不可见资产的生命周期事件不返回
        where.append(this.dataScope.assetSql(com.dcim.security.CurrentUser.get(), "a", args));

        Long total = (Long)this.jdbc.queryForObject("SELECT COUNT(*) FROM asset_event e" + where, Long.class, args.toArray());
        List<Object> pa = new ArrayList<>(args);
        pa.add(s);
        pa.add((p - 1L) * s);
        List<Map<String, Object>> rows = this.jdbc
            .queryForList(
                "SELECT e.id, e.asset_no AS \"assetNo\", a.name AS \"assetName\", a.status_biz AS \"statusBiz\", a.deleted_at IS NOT NULL AS deleted, e.event_type AS \"eventType\", e.operator, e.ref_doc AS \"refDoc\", e.event_time AS \"eventTime\" FROM asset_event e LEFT JOIN asset a ON a.asset_no = e.asset_no"
                    + where
                    + " ORDER BY e.event_time DESC, e.id DESC LIMIT ? OFFSET ?",
                pa.toArray()
            );
        return ApiResult.ok(new PageResult<>(total == null ? 0L : total, rows));
    }

    @GetMapping({"/stats"})
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<Map<String, Object>> stats(@RequestParam(defaultValue = "30") int days) {
        int d = Math.max(1, Math.min(days, 366));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(
            "byType",
            this.jdbc
                .queryForList(
                    "SELECT event_type AS key, COUNT(*) AS count FROM asset_event WHERE event_time >= NOW() - make_interval(days => ?) GROUP BY 1 ORDER BY 2 DESC",
                    new Object[]{d}
                )
        );
        out.put(
            "daily",
            this.jdbc
                .queryForList(
                    "SELECT to_char(event_time::date, 'YYYY-MM-DD') AS day, COUNT(*) AS count FROM asset_event WHERE event_time >= NOW() - make_interval(days => ?) GROUP BY 1 ORDER BY 1",
                    new Object[]{d}
                )
        );
        out.put(
            "byStatus", this.jdbc.queryForList("SELECT status_biz AS key, COUNT(*) AS count FROM asset WHERE deleted_at IS NULL GROUP BY 1 ORDER BY 2 DESC")
        );
        out.put(
            "warrantyExpiring",
            this.jdbc
                .queryForObject("SELECT COUNT(*) FROM asset WHERE deleted_at IS NULL AND warranty_end BETWEEN CURRENT_DATE AND CURRENT_DATE + 90", Long.class)
        );
        return ApiResult.ok(out);
    }

    
    public AssetLifecycleController(final JdbcTemplate jdbc, final com.dcim.security.DataScopeService dataScope) {
        this.jdbc = jdbc;
        this.dataScope = dataScope;
    }
}
