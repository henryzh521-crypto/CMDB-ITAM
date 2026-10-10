package com.dcim.monitoring.service;

import com.dcim.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PrometheusClient {
    private static final Pattern SAFE_LABEL = Pattern.compile("^[A-Za-z0-9_.:\\-]{1,100}$");
    private static final long TARGET_HEALTH_TTL_MS = 30_000L;
    private final MonitoringSettings settings;
    private final ObjectMapper om;
    private final RestClient rest;
    private volatile Map<String, Map<String, Object>> targetHealthCache;
    private volatile long targetHealthCachedAt;

    public PrometheusClient(MonitoringSettings settings, ObjectMapper om) {
        this.settings = settings;
        this.om = om;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(15000);
        this.rest = RestClient.builder().requestFactory(f).build();
    }

    public boolean configured() {
        String u = this.settings.prometheusUrl();
        return u != null && !u.isBlank();
    }

    public static String label(String v) {
        if (v != null && SAFE_LABEL.matcher(v).matches()) {
            return v;
        } else {
            throw BusinessException.badRequest("非法的标签值：" + v);
        }
    }

    private JsonNode get(String path, Map<String, String> params) {
        if (!this.configured()) {
            throw new BusinessException(503, "未配置 Prometheus 地址（监控设置 / dcim.monitoring.prometheus-url）");
        } else {
            StringBuilder q = new StringBuilder();
            params.forEach((k, v) -> q.append(q.length() == 0 ? "?" : "&").append(k).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8)));
            URI uri = URI.create(this.settings.prometheusUrl().replaceAll("/+$", "") + path + q);

            try {
                String body = (String)this.rest.get().uri(uri).retrieve().body(String.class);
                JsonNode n = this.om.readTree(body);
                if (!"success".equals(n.path("status").asText())) {
                    throw new BusinessException(502, "Prometheus 查询失败：" + n.path("error").asText());
                } else {
                    return n.path("data");
                }
            } catch (RestClientException var7) {
                throw new BusinessException(502, "无法连接 Prometheus：" + var7.getMessage());
            } catch (BusinessException var8) {
                throw var8;
            } catch (Exception var9) {
                throw new BusinessException(502, "Prometheus 响应解析失败：" + var9.getMessage());
            }
        }
    }

    public List<Map<String, Object>> instant(String query) {
        JsonNode data = this.get("/api/v1/query", Map.of("query", query));
        List<Map<String, Object>> out = new ArrayList<>();

        for (JsonNode r : data.path("result")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("metric", this.om.convertValue(r.path("metric"), Map.class));
            m.put("value", num(r.path("value").path(1).asText()));
            out.add(m);
        }

        return out;
    }

    public List<Map<String, Object>> range(String query, Instant start, Instant end, int stepSec) {
        JsonNode data = this.get(
            "/api/v1/query_range",
            Map.of(
                "query",
                query,
                "start",
                String.valueOf(start.getEpochSecond()),
                "end",
                String.valueOf(end.getEpochSecond()),
                "step",
                String.valueOf(Math.max(15, stepSec))
            )
        );
        List<Map<String, Object>> out = new ArrayList<>();

        for (JsonNode r : data.path("result")) {
            List<Object[]> pts = new ArrayList<>();

            for (JsonNode p : r.path("values")) {
                pts.add(new Object[]{p.path(0).asLong(), num(p.path(1).asText())});
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("metric", this.om.convertValue(r.path("metric"), Map.class));
            m.put("points", pts);
            out.add(m);
        }

        return out;
    }

    public Map<String, Map<String, Object>> targetHealth() {
        // /api/v1/targets 在目标数多时单次就要 100~300ms，而 target 健康状态本身随
        // scrape 周期（15~30s）才变化一次：缓存 30s 既保住监控总览的交互预算，又不损失新鲜度。
        // 失败（BusinessException）不缓存，避免 Prometheus 抖动把错误态钉死 30 秒。
        Map<String, Map<String, Object>> cached = this.targetHealthCache;
        if (cached != null && System.currentTimeMillis() - this.targetHealthCachedAt < TARGET_HEALTH_TTL_MS) {
            return cached;
        } else {
            JsonNode data = this.get("/api/v1/targets", Map.of("state", "active"));
            Map<String, Map<String, Object>> out = new LinkedHashMap<>();

            for (JsonNode t : data.path("activeTargets")) {
                String job = t.path("labels").path("job").asText(t.path("scrapePool").asText());
                Map<String, Object> s = out.computeIfAbsent(job, k -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("up", 0);
                    m.put("down", 0);
                    m.put("total", 0);
                    m.put("errors", new ArrayList());
                    return m;
                });
                boolean up = "up".equals(t.path("health").asText());
                s.put(up ? "up" : "down", (Integer)s.get(up ? "up" : "down") + 1);
                s.put("total", (Integer)s.get("total") + 1);
                List<Map<String, String>> errs = (List<Map<String, String>>)s.get("errors");
                if (!up && errs.size() < 5) {
                    Map<String, String> e = new LinkedHashMap<>();
                    e.put("instance", t.path("labels").path("instance").asText());
                    e.put("assetNo", t.path("labels").path("asset_no").asText(null));
                    e.put("error", t.path("lastError").asText());
                    errs.add(e);
                }
            }

            this.targetHealthCache = out;
            this.targetHealthCachedAt = System.currentTimeMillis();
            return out;
        }
    }

    public List<Map<String, Object>> rules() {
        JsonNode data = this.get("/api/v1/rules", Map.of("type", "alert"));
        List<Map<String, Object>> out = new ArrayList<>();

        for (JsonNode g : data.path("groups")) {
            for (JsonNode r : g.path("rules")) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("group", g.path("name").asText());
                m.put("name", r.path("name").asText());
                m.put("query", r.path("query").asText());
                m.put("duration", r.path("duration").asLong());
                m.put("severity", r.path("labels").path("severity").asText(null));
                m.put("summary", r.path("annotations").path("summary").asText(null));
                m.put("state", r.path("state").asText());
                m.put("firing", r.path("alerts").size());
                m.put("health", r.path("health").asText());
                out.add(m);
            }
        }

        return out;
    }

    private static Double num(String s) {
        try {
            double d = Double.parseDouble(s);
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException var3) {
            return null;
        }
    }
}
