package com.dcim.monitoring.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.monitoring.entity.MonitorProfile;
import com.dcim.monitoring.mapper.MonitorProfileMapper;
import com.dcim.monitoring.service.MonitoringSettings;
import com.dcim.monitoring.service.PrometheusClient;
import com.dcim.monitoring.service.ServiceDiscoveryService;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/monitoring"})
public class MonitoringController {
    private static final Pattern JOB = Pattern.compile("^[a-z0-9_\\-]{1,30}$");
    private static final Set<String> IP_FIELDS = Set.of("biz_ip", "mgmt_ip");
    private final PrometheusClient prom;
    private final MonitoringSettings settings;
    private final MonitorProfileMapper profileMapper;
    private final ServiceDiscoveryService sd;
    private final JdbcTemplate jdbc;
    private volatile Object[] monitoredCountCache;

    @GetMapping({"/overview"})
    @RequirePerm(
        module = "monitoring"
    )
    public ApiResult<Map<String, Object>> overview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("prometheusConfigured", this.prom.configured());
        Map<String, Object> jobs = new LinkedHashMap<>();

        for (String job : this.sd.jobs()) {
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("cmdbTargets", this.sd.countTargetsCached(job));
            jobs.put(job, j);
        }

        if (this.prom.configured()) {
            try {
                this.prom.targetHealth().forEach((jobx, h) -> {
                    Map<String, Object> jx = (Map<String, Object>)jobs.computeIfAbsent(jobx, k -> new LinkedHashMap());
                    jx.putAll((Map<? extends String, ? extends Object>)h);
                });
                out.put("prometheusUp", true);
            } catch (BusinessException var6) {
                out.put("prometheusUp", false);
                out.put("prometheusError", var6.getMessage());
            }
        }

        out.put("jobs", jobs);
        out.put("monitoredAssets", this.countMonitoredAssets());
        return ApiResult.ok(out);
    }

    private long countMonitoredAssets() {
        // 10 万行级资产表按「受监控状态 + 有 IP」做 COUNT 要扫数万索引行回表（实测 85ms+），
        // 而总览页其余数字（countTargetsCached）均带 60s 缓存——这里对齐，避免它独自拖慢监控总览。
        // 监控设置变更走 MonitoringSettings.invalidate()/save 时同步失效，普通资产增删的 60s 滞后可接受。
        long now = System.currentTimeMillis();
        Object[] c = this.monitoredCountCache;
        if (c != null && now - (Long) c[0] < 60_000L) {
            return (Long) c[1];
        }
        List<String> st = this.settings.monitoredStatuses();
        String in = String.join(",", Collections.nCopies(st.size(), "?"));
        Long n = (Long)this.jdbc
            .queryForObject(
                "SELECT COUNT(*) FROM asset WHERE deleted_at IS NULL AND status_biz IN ("
                    + in
                    + ") AND (NULLIF(TRIM(biz_ip),'') IS NOT NULL OR NULLIF(TRIM(mgmt_ip),'') IS NOT NULL) AND COALESCE(attrs ->> 'monitor', 'true') <> 'false'",
                Long.class,
                st.toArray()
            );
        long v = n == null ? 0L : n;
        this.monitoredCountCache = new Object[]{now, v};
        return v;
    }

    @GetMapping({"/assets/{assetNo}/metrics"})
    @RequirePerm(
        module = "monitoring"
    )
    public ApiResult<Map<String, Object>> assetMetrics(@PathVariable String assetNo, @RequestParam(defaultValue = "6") int hours) {
        String a = PrometheusClient.label(assetNo);
        int h = Math.max(1, Math.min(hours, 168));
        Instant end = Instant.now();
        Instant start = end.minusSeconds((long)h * 3600L);
        int step = Math.max(15, h * 3600 / 240);
        String sel = "asset_no=\"" + a + "\"";
        Map<String, String> panels = new LinkedHashMap<>();
        panels.put("ping", "probe_success{" + sel + "}");
        panels.put("pingRtt", "probe_duration_seconds{" + sel + "} * 1000");
        panels.put("cpu", "100 - avg by (asset_no) (rate(node_cpu_seconds_total{" + sel + ",mode=\"idle\"}[5m])) * 100");
        panels.put("mem", "(1 - node_memory_MemAvailable_bytes{" + sel + "} / node_memory_MemTotal_bytes{" + sel + "}) * 100");
        panels.put(
            "disk",
            "max by (asset_no) ((1 - node_filesystem_avail_bytes{"
                + sel
                + ",fstype!~\"tmpfs|overlay|squashfs|ramfs\"} / node_filesystem_size_bytes{"
                + sel
                + ",fstype!~\"tmpfs|overlay|squashfs|ramfs\"}) * 100)"
        );
        panels.put("load", "node_load5{" + sel + "}");
        panels.put("netIn", "sum by (asset_no) (rate(node_network_receive_bytes_total{" + sel + ",device!~\"lo|veth.*|docker.*|br-.*\"}[5m])) * 8");
        panels.put("netOut", "sum by (asset_no) (rate(node_network_transmit_bytes_total{" + sel + ",device!~\"lo|veth.*|docker.*|br-.*\"}[5m])) * 8");
        panels.put("gpuUtil", "avg by (asset_no) (DCGM_FI_DEV_GPU_UTIL{" + sel + "})");
        panels.put("gpuTemp", "max by (asset_no) (DCGM_FI_DEV_GPU_TEMP{" + sel + "})");
        panels.put(
            "gpuMem", "avg by (asset_no) (DCGM_FI_DEV_FB_USED{" + sel + "} / (DCGM_FI_DEV_FB_USED{" + sel + "} + DCGM_FI_DEV_FB_FREE{" + sel + "}) * 100)"
        );
        panels.put("power", "sum by (asset_no) (ipmi_dcmi_power_consumption_watts{" + sel + "})");
        panels.put("inletTemp", "max by (asset_no) (ipmi_temperature_celsius{" + sel + ",name=~\"(?i).*inlet.*|.*ambient.*\"})");
        Map<String, Object> series = new LinkedHashMap<>();
        Map<String, Object> current = new LinkedHashMap<>();

        for (Entry<String, String> p : panels.entrySet()) {
            List<Map<String, Object>> r = this.prom.range(p.getValue(), start, end, step);
            if (!r.isEmpty()) {
                series.put(p.getKey(), r);
                List<Map<String, Object>> now = this.prom.instant(p.getValue());
                if (!now.isEmpty()) {
                    current.put(p.getKey(), now.size() == 1 ? now.get(0).get("value") : now);
                }
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("assetNo", a);
        out.put("hours", h);
        out.put("series", series);
        out.put("current", current);
        out.put("up", this.prom.instant("up{" + sel + "}"));
        return ApiResult.ok(out);
    }

    @GetMapping({"/rules"})
    @RequirePerm(
        module = "monitoring"
    )
    public ApiResult<List<Map<String, Object>>> rules() {
        return ApiResult.ok(this.prom.rules());
    }

    @GetMapping({"/profiles"})
    @RequirePerm(
        module = "monitoring"
    )
    public ApiResult<List<Map<String, Object>>> profiles() {
        List<Map<String, Object>> out = new ArrayList<>();

        for (MonitorProfile p : this.profileMapper
            .selectList((Wrappers.<MonitorProfile>lambdaQuery().orderByAsc(MonitorProfile::getJob)).orderByAsc(MonitorProfile::getId))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("job", p.getJob());
            m.put("categoryPattern", p.getCategoryPattern());
            m.put("ipField", p.getIpField());
            m.put("port", p.getPort());
            m.put("enabled", p.getEnabled());
            m.put("remark", p.getRemark());
            out.add(m);
        }

        return ApiResult.ok(out);
    }

    @PostMapping({"/profiles"})
    @RequirePerm(
        module = "monitoring",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "monitoring",
        objType = "monitor_profile",
        objIdSpEL = "#req.job()"
    )
    public ApiResult<MonitorProfile> createProfile(@RequestBody MonitoringController.ProfileReq req) {
        requireAdmin();
        MonitorProfile p = new MonitorProfile();
        this.apply(p, req);

        try {
            this.profileMapper.insert(p);
        } catch (DuplicateKeyException var4) {
            throw BusinessException.conflict("相同任务 / 分类匹配 / IP 字段的模板已存在");
        }

        this.sd.invalidate();
        return ApiResult.ok((MonitorProfile)this.profileMapper.selectById(p.getId()));
    }

    @PutMapping({"/profiles/{id}"})
    @RequirePerm(
        module = "monitoring",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "monitoring",
        objType = "monitor_profile",
        objIdSpEL = "#id"
    )
    public ApiResult<MonitorProfile> updateProfile(@PathVariable Long id, @RequestBody MonitoringController.ProfileReq req) {
        requireAdmin();
        MonitorProfile p = (MonitorProfile)this.profileMapper.selectById(id);
        if (p == null) {
            throw BusinessException.notFound("监控模板不存在");
        } else {
            this.apply(p, req);
            p.setUpdatedAt(LocalDateTime.now());
            this.profileMapper.updateById(p);
            if (req.port() == null) {
                this.profileMapper
                    .update(null, (Wrappers.<MonitorProfile>lambdaUpdate().eq(MonitorProfile::getId, id)).set(MonitorProfile::getPort, null));
            }

            this.sd.invalidate();
            return ApiResult.ok((MonitorProfile)this.profileMapper.selectById(id));
        }
    }

    @DeleteMapping({"/profiles/{id}"})
    @RequirePerm(
        module = "monitoring",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "monitoring",
        objType = "monitor_profile",
        objIdSpEL = "#id"
    )
    public ApiResult<Void> deleteProfile(@PathVariable Long id) {
        requireAdmin();
        this.profileMapper.deleteById(id);
        this.sd.invalidate();
        return ApiResult.ok();
    }

    @GetMapping({"/profiles/preview"})
    @RequirePerm(
        module = "monitoring"
    )
    public ApiResult<Map<String, Object>> preview(@RequestParam String job) {
        if (!JOB.matcher(job).matches()) {
            throw BusinessException.badRequest("任务名非法");
        } else {
            List<Map<String, Object>> all = this.sd.targets(job, 1, 0);
            return ApiResult.ok(Map.of("total", all.size(), "targets", all.subList(0, Math.min(200, all.size()))));
        }
    }

    private void apply(MonitorProfile p, MonitoringController.ProfileReq r) {
        if (r.job() == null || !JOB.matcher(r.job().trim()).matches()) {
            throw BusinessException.badRequest("任务名只能包含小写字母、数字、- 和 _");
        } else if (r.ipField() != null && IP_FIELDS.contains(r.ipField())) {
            if (r.port() == null || r.port() >= 1 && r.port() <= 65535) {
                String pattern = r.categoryPattern() != null && !r.categoryPattern().isBlank() ? r.categoryPattern().trim() : "%";
                if (pattern.length() > 100) {
                    throw BusinessException.badRequest("分类匹配不超过 100 字符");
                } else {
                    p.setJob(r.job().trim());
                    p.setCategoryPattern(pattern);
                    p.setIpField(r.ipField());
                    p.setPort(r.port());
                    p.setEnabled(r.enabled() == null || r.enabled());
                    p.setRemark(r.remark());
                }
            } else {
                throw BusinessException.badRequest("端口须在 1-65535");
            }
        } else {
            throw BusinessException.badRequest("IP 字段须为 biz_ip（业务 IP）或 mgmt_ip（管理/带外 IP）");
        }
    }

    @GetMapping({"/settings"})
    @RequirePerm(
        module = "monitoring"
    )
    public ApiResult<Map<String, Object>> getSettings() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("values", this.jdbc.queryForList("SELECT setting_key AS key, setting_value AS value, remark FROM monitoring_setting ORDER BY setting_key"));
        out.put("tokenConfigured", this.settings.ingestToken() != null && !this.settings.ingestToken().isBlank());
        out.put("effectivePrometheusUrl", this.settings.prometheusUrl());
        out.put("sdJobs", this.sd.jobs());
        return ApiResult.ok(out);
    }

    @PutMapping({"/settings"})
    @RequirePerm(
        module = "monitoring",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "monitoring",
        objType = "monitoring_setting"
    )
    public ApiResult<Void> putSettings(@RequestBody Map<String, String> values) {
        requireAdmin();

        for (Entry<String, String> e : values.entrySet()) {
            if (!MonitoringSettings.KEYS.contains(e.getKey())) {
                throw BusinessException.badRequest("未知设置项：" + e.getKey());
            }

            String v = e.getValue() == null ? "" : e.getValue().trim();
            if (v.length() > 500) {
                throw BusinessException.badRequest("设置值过长：" + e.getKey());
            }

            this.validate(e.getKey(), v);
            this.jdbc
                .update(
                    "INSERT INTO monitoring_setting (setting_key, setting_value) VALUES (?, ?) ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value, updated_at = NOW()",
                    new Object[]{e.getKey(), v}
                );
        }

        this.settings.invalidate();
        this.sd.invalidate();
        return ApiResult.ok();
    }

    private void validate(String key, String v) {
        switch (key) {
            case "auto_ticket_severity":
                if (!Set.of("critical", "warning", "info", "none").contains(v)) {
                    throw BusinessException.badRequest("自动派单级别须为 critical / warning / info / none");
                }
                break;
            case "auto_ticket_delay_min":
                if (!v.matches("\\d{1,4}")) {
                    throw BusinessException.badRequest("派单延迟须为 0-9999 的整数（分钟）");
                }
                break;
            case "ticket_priority_critical":
            case "ticket_priority_warning":
                if (!Set.of("高", "中", "低").contains(v)) {
                    throw BusinessException.badRequest("工单优先级须为 高 / 中 / 低");
                }
                break;
            case "resolve_comment":
                if (!Set.of("true", "false").contains(v)) {
                    throw BusinessException.badRequest("resolve_comment 须为 true / false");
                }
                break;
            case "prometheus_url":
                if (!v.isEmpty() && !v.matches("^https?://[A-Za-z0-9.\\-:\\[\\]]+(/[A-Za-z0-9._\\-/]*)?$")) {
                    throw BusinessException.badRequest("Prometheus 地址格式不正确");
                }
                break;
            case "monitored_statuses":
                if (v.isEmpty()) {
                    throw BusinessException.badRequest("至少保留一个纳入监控的资产状态");
                }
        }
    }

    private static void requireAdmin() {
        LoginUser u = CurrentUser.get();
        if (u == null || !u.isAdmin()) {
            throw BusinessException.forbidden("仅管理员可修改监控配置");
        }
    }

    
    public MonitoringController(
        final PrometheusClient prom,
        final MonitoringSettings settings,
        final MonitorProfileMapper profileMapper,
        final ServiceDiscoveryService sd,
        final JdbcTemplate jdbc
    ) {
        this.prom = prom;
        this.settings = settings;
        this.profileMapper = profileMapper;
        this.sd = sd;
        this.jdbc = jdbc;
    }

    public static record ProfileReq(String job, String categoryPattern, String ipField, Integer port, Boolean enabled, String remark) {
    }
}
