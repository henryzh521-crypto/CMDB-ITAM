package com.dcim.monitoring.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.monitoring.entity.MonitorProfile;
import com.dcim.monitoring.mapper.MonitorProfileMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ServiceDiscoveryService {
    private static final Set<String> IP_FIELDS = Set.of("biz_ip", "mgmt_ip");
    private static final Pattern ADDR = Pattern.compile("^[A-Za-z0-9.:\\-\\[\\]_]{1,253}$");
    private final JdbcTemplate jdbc;
    private final MonitorProfileMapper profileMapper;
    private final MonitoringSettings settings;
    private static final long CACHE_MS = 30000L;
    private final ConcurrentHashMap<String, Object[]> cache = new ConcurrentHashMap<>();

    public ServiceDiscoveryService(JdbcTemplate jdbc, MonitorProfileMapper profileMapper, MonitoringSettings settings) {
        this.jdbc = jdbc;
        this.profileMapper = profileMapper;
        this.settings = settings;
    }

    public void invalidate() {
        this.cache.clear();
        this.jsonCache.clear();
        this.countCache.clear();
    }

    /** 预序列化 + gzip 的服务发现结果：raw JSON 与 gzip 两份字节 */
    public record SdPayload(byte[] json, byte[] gzip, int groups, long builtAt) {
    }

    private final ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<SdPayload>> jsonCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object[]> countCache = new ConcurrentHashMap<>();
    private final com.fasterxml.jackson.databind.ObjectMapper sdMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * v3.3：10 万台规模下 icmp 任务的 SD 响应约 39MB，v3.2 每次抓取都要把 20 万个 Map 重新序列化一遍
     * （单次 0.7~2.6s CPU），且缓存过期瞬间多个 Prometheus 分片会同时回源（缓存击穿）。
     * 现在：同一 key 只回源一次（single-flight），结果预序列化并预压缩，命中时直接写字节。
     */
    public SdPayload cachedPayload(String job, int shards, int shard) {
        String key = job + "|" + shards + "|" + shard;
        long now = System.currentTimeMillis();
        java.util.concurrent.CompletableFuture<SdPayload> f = this.jsonCache.get(key);
        if (f != null && f.isDone() && !f.isCompletedExceptionally() && now - f.join().builtAt() < CACHE_MS) {
            return f.join();
        }
        java.util.concurrent.CompletableFuture<SdPayload> mine = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CompletableFuture<SdPayload> cur = this.jsonCache.compute(key, (k, old) -> {
            if (old != null && (!old.isDone() || (!old.isCompletedExceptionally() && now - old.join().builtAt() < CACHE_MS))) {
                return old;
            }
            return mine;
        });
        if (cur != mine) {
            return cur.join();
        }
        try {
            List<Map<String, Object>> t = this.targets(job, shards, shard);
            byte[] json = this.sdMapper.writeValueAsBytes(t);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(Math.max(1024, json.length / 8));
            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bos)) {
                gz.write(json);
            }
            SdPayload p = new SdPayload(json, bos.toByteArray(), t.size(), System.currentTimeMillis());
            mine.complete(p);
            return p;
        } catch (Exception e) {
            mine.completeExceptionally(e);
            this.jsonCache.remove(key, mine);
            throw (e instanceof RuntimeException re) ? re : new IllegalStateException(e);
        }
    }

    public List<Map<String, Object>> cachedTargets(String job, int shards, int shard) {
        String key = job + "|" + shards + "|" + shard;
        Object[] c = this.cache.get(key);
        long now = System.currentTimeMillis();
        if (c != null && now - (Long)c[0] < 30000L) {
            return (List<Map<String, Object>>)c[1];
        } else {
            List<Map<String, Object>> t = this.targets(job, shards, shard);
            this.cache.put(key, new Object[]{now, t});
            return t;
        }
    }

    /** 监控总览用：目标数缓存 60 秒（v3.2 每次打开总览都对 10 万资产跑 5 个 UNION 正则扫描，约 3.4s） */
    public long countTargetsCached(String job) {
        Object[] c = this.countCache.get(job);
        long now = System.currentTimeMillis();
        if (c != null && now - (Long) c[0] < 60000L) {
            return (Long) c[1];
        }
        long n = this.countTargets(job);
        this.countCache.put(job, new Object[]{now, n});
        return n;
    }

    public long countTargets(String job) {
        List<MonitorProfile> profiles = this.profileMapper
            .selectList((Wrappers.<MonitorProfile>lambdaQuery().eq(MonitorProfile::getJob, job)).eq(MonitorProfile::getEnabled, true));
        List<String> statuses = this.settings.monitoredStatuses();
        String inStatuses = String.join(",", Collections.nCopies(statuses.size(), "?"));
        List<String> parts = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        for (MonitorProfile p : profiles) {
            String ipField = p.getIpField();
            if (IP_FIELDS.contains(ipField)) {
                parts.add(
                    "SELECT a.asset_no, btrim(a."
                        + ipField
                        + ") || ':' || "
                        + (p.getPort() == null ? "''" : String.valueOf(p.getPort()))
                        + " AS addr FROM asset a LEFT JOIN category c ON c.category_code = a.category_code WHERE a.deleted_at IS NULL AND a.status_biz IN ("
                        + inStatuses
                        + ") AND btrim(a."
                        + ipField
                        + ") ~ '^[A-Za-z0-9.:\\-\\[\\]_]{1,253}$' AND COALESCE(c.path, '/' || COALESCE(a.category_code, '') || '/') LIKE ? AND COALESCE(a.attrs ->> 'monitor', 'true') <> 'false'"
                );
                args.addAll(statuses);
                args.add(p.getCategoryPattern() != null && !p.getCategoryPattern().isBlank() ? p.getCategoryPattern() : "%");
            }
        }

        if (parts.isEmpty()) {
            return 0L;
        } else {
            Long n = (Long)this.jdbc.queryForObject("SELECT COUNT(*) FROM (" + String.join(" UNION ", parts) + ") t", Long.class, args.toArray());
            return n == null ? 0L : n;
        }
    }

    public List<String> jobs() {
        return this.profileMapper
            .selectList(Wrappers.<MonitorProfile>lambdaQuery().eq(MonitorProfile::getEnabled, true))
            .stream()
            .map(MonitorProfile::getJob)
            .distinct()
            .sorted()
            .toList();
    }

    public List<Map<String, Object>> targets(String job, int shards, int shard) {
        List<MonitorProfile> profiles = this.profileMapper
            .selectList((Wrappers.<MonitorProfile>lambdaQuery().eq(MonitorProfile::getJob, job)).eq(MonitorProfile::getEnabled, true));
        if (profiles.isEmpty()) {
            return List.of();
        } else {
            List<String> statuses = this.settings.monitoredStatuses();
            String inStatuses = String.join(",", Collections.nCopies(statuses.size(), "?"));
            Set<String> seen = new LinkedHashSet<>();
            List<Map<String, Object>> out = new ArrayList<>();

            for (MonitorProfile p : profiles) {
                String ipField = p.getIpField();
                if (IP_FIELDS.contains(ipField)) {
                    List<Object> args = new ArrayList<>(statuses);
                    args.add(p.getCategoryPattern() != null && !p.getCategoryPattern().isBlank() ? p.getCategoryPattern() : "%");
                    List<Map<String, Object>> rows = this.jdbc
                        .queryForList(
                            "SELECT a.asset_no, a.name, a."
                                + ipField
                                + " AS ip, a.rack_code, a.room_code, a.region_code, a.category_code, a.sn, a.model_code, a.status_biz FROM asset a LEFT JOIN category c ON c.category_code = a.category_code WHERE a.deleted_at IS NULL AND a.status_biz IN ("
                                + inStatuses
                                + ") AND NULLIF(TRIM(a."
                                + ipField
                                + "), '') IS NOT NULL AND COALESCE(c.path, '/' || COALESCE(a.category_code, '') || '/') LIKE ? AND COALESCE(a.attrs ->> 'monitor', 'true') <> 'false' ORDER BY a.asset_no",
                            args.toArray()
                        );
                    String role = "biz_ip".equals(ipField) ? "biz" : "mgmt";

                    for (Map<String, Object> r : rows) {
                        String ip = String.valueOf(r.get("ip")).trim();
                        if (ADDR.matcher(ip).matches()) {
                            String address = p.getPort() == null ? ip : (ip.contains(":") && !ip.startsWith("[") ? "[" + ip + "]" : ip) + ":" + p.getPort();
                            String key = r.get("asset_no") + "|" + address;
                            if (seen.add(key) && (shards <= 1 || Math.floorMod(key.hashCode(), shards) == shard)) {
                                Map<String, String> labels = new LinkedHashMap<>();
                                put(labels, "asset_no", r.get("asset_no"));
                                put(labels, "asset_name", r.get("name"));
                                put(labels, "rack", r.get("rack_code"));
                                put(labels, "room", r.get("room_code"));
                                put(labels, "region", r.get("region_code"));
                                put(labels, "category", r.get("category_code"));
                                put(labels, "sn", r.get("sn"));
                                put(labels, "model", r.get("model_code"));
                                put(labels, "asset_status", r.get("status_biz"));
                                labels.put("ip_role", role);
                                Map<String, Object> group = new LinkedHashMap<>();
                                group.put("targets", List.of(address));
                                group.put("labels", labels);
                                out.add(group);
                            }
                        }
                    }
                }
            }

            return out;
        }
    }

    private static void put(Map<String, String> m, String k, Object v) {
        if (v != null && !String.valueOf(v).isBlank()) {
            m.put(k, String.valueOf(v));
        }
    }
}
