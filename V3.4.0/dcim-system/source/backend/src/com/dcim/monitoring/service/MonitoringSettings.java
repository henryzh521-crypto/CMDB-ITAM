package com.dcim.monitoring.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MonitoringSettings {
    public static final Set<String> KEYS = Set.of(
        "auto_ticket_severity",
        "auto_ticket_delay_min",
        "monitored_statuses",
        "ticket_priority_critical",
        "ticket_priority_warning",
        "resolve_comment",
        "prometheus_url"
    );
    private final JdbcTemplate jdbc;
    private final String deployPrometheusUrl;
    private final String ingestToken;
    private volatile Map<String, String> cache = Map.of();
    private volatile long loadedAt;

    public MonitoringSettings(
        JdbcTemplate jdbc, @Value("${dcim.monitoring.prometheus-url:}") String deployPrometheusUrl, @Value("${dcim.monitoring.token:}") String ingestToken
    ) {
        this.jdbc = jdbc;
        this.deployPrometheusUrl = deployPrometheusUrl;
        this.ingestToken = ingestToken;
    }

    public Map<String, String> all() {
        if (System.currentTimeMillis() - this.loadedAt > 30000L) {
            Map<String, String> m = new LinkedHashMap<>();
            this.jdbc.query("SELECT setting_key, setting_value FROM monitoring_setting ORDER BY setting_key", (org.springframework.jdbc.core.RowCallbackHandler) rs -> { m.put(rs.getString(1), rs.getString(2)); });
            this.cache = m;
            this.loadedAt = System.currentTimeMillis();
        }

        return this.cache;
    }

    public void invalidate() {
        this.loadedAt = 0L;
    }

    public String get(String key, String def) {
        String v = this.all().get(key);
        return v != null && !v.isBlank() ? v.trim() : def;
    }

    public List<String> monitoredStatuses() {
        return List.of(this.get("monitored_statuses", "在用(计费),维修中").split("\\s*,\\s*"));
    }

    public int autoTicketDelayMinutes() {
        try {
            return Math.max(0, Integer.parseInt(this.get("auto_ticket_delay_min", "5")));
        } catch (NumberFormatException var2) {
            return 5;
        }
    }

    public String autoTicketSeverity() {
        return this.get("auto_ticket_severity", "critical").toLowerCase();
    }

    public String prometheusUrl() {
        String v = this.get("prometheus_url", "");
        return v.isBlank() ? this.deployPrometheusUrl : v;
    }

    public String ingestToken() {
        return this.ingestToken;
    }

    public static int rank(String severity) {
        if (severity == null) {
            return 0;
        } else {
            String var1 = severity.toLowerCase();

            return switch (var1) {
                case "critical", "disaster", "high", "p1" -> 3;
                case "warning", "average", "major", "p2" -> 2;
                case "info", "information", "minor", "low", "p3" -> 1;
                default -> 0;
            };
        }
    }
}
