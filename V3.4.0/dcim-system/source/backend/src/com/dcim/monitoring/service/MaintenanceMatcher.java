package com.dcim.monitoring.service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceMatcher {
    private final JdbcTemplate jdbc;

    public MaintenanceMatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String reason(String assetNo, String rackCode, String roomCode, String alertname, String assetStatus, LocalDateTime at) {
        if ("维修中".equals(assetStatus)) {
            return "资产维修中";
        } else {
            List<String> windows = this.jdbc
                .queryForList(
                    "SELECT name FROM maintenance_window\nWHERE deleted_at IS NULL AND starts_at <= ? AND ends_at > ?\n  AND ( (cardinality(asset_nos) = 0 AND cardinality(rack_codes) = 0 AND cardinality(room_codes) = 0)\n        OR (?::text IS NOT NULL AND ?::text = ANY(asset_nos))\n        OR (?::text IS NOT NULL AND ?::text = ANY(rack_codes))\n        OR (?::text IS NOT NULL AND ?::text = ANY(room_codes)) )\n  AND (cardinality(alertnames) = 0 OR ?::text = ANY(alertnames))\nORDER BY starts_at LIMIT 1\n",
                    String.class,
                    new Object[]{at, at, assetNo, assetNo, rackCode, rackCode, roomCode, roomCode, alertname}
                );
            if (!windows.isEmpty()) {
                return "维护窗口：" + windows.get(0);
            } else {
                if (assetNo != null) {
                    List<String> changes = this.jdbc
                        .queryForList(
                            "SELECT c.cr_no FROM change_request c JOIN change_asset ca ON ca.cr_id = c.id\nWHERE ca.asset_no = ? AND c.deleted_at IS NULL\n  AND (c.status = 'IN_PROGRESS'\n       OR (c.status = 'APPROVED' AND c.scheduled_start <= ? AND c.scheduled_end > ?))\nORDER BY c.id DESC LIMIT 1\n",
                            String.class,
                            new Object[]{assetNo, at, at}
                        );
                    if (!changes.isEmpty()) {
                        return "变更实施中：" + changes.get(0);
                    }
                }

                return null;
            }
        }
    }
}
