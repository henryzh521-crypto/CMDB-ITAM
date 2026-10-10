package com.dcim.metrics;

import com.dcim.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;
import org.springframework.web.client.RestClient.RequestBodySpec;

@Component
@ConditionalOnProperty(
    name = {"dcim.metrics.store"},
    havingValue = "tdengine"
)
public class TdengineMetricStore implements MetricStore {
    
    private static final Logger log = LoggerFactory.getLogger(TdengineMetricStore.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<Map<String, Object>>() {
    };
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final RestClient rest;
    private final String database;

    public TdengineMetricStore(
        @Value("${dcim.metrics.tdengine.url:http://localhost:6041}") String url,
        @Value("${dcim.metrics.tdengine.username:root}") String user,
        @Value("${dcim.metrics.tdengine.password:taosdata}") String password,
        @Value("${dcim.metrics.tdengine.database:dcim_metrics}") String database,
        Builder builder
    ) {
        this.database = database;
        String basic = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        // 修复：此前未设超时，TDengine 假死/网络黑洞时调用线程会被无限阻塞；若发生在 @Scheduled
        // 任务里（GPU 健康度评分→queryWindow），ScheduledJobLockAspect 的心跳续租会让集群锁被
        // 无限期占用。对齐 PrometheusClient 的 3s/15s；全部读写都经 exec() 走 this.rest 单客户端，
        // 在 requestFactory 上设一次即覆盖 ensureSchema/saveGpuMetrics/queryWindow/recentSeries 全部入口。
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(15000);
        this.rest = builder.requestFactory(f).baseUrl(url).defaultHeader("Authorization", new String[]{"Basic " + basic}).build();
    }

    @Override
    public void ensureSchema() {
        this.exec("CREATE DATABASE IF NOT EXISTS " + this.database + " KEEP 180 DURATION 10 PRECISION 'ms'");
        this.exec(
            "CREATE STABLE IF NOT EXISTS "
                + this.database
                + ".gpu_metric (ts TIMESTAMP, temp_c FLOAT, power_w FLOAT, util_pct FLOAT, ecc_dbe INT, xid_errors INT) TAGS (card_no BINARY(64), host_asset_no BINARY(50))"
        );
        log.info("[metrics] TDengine schema 就绪（db={}，KEEP 180 天）", this.database);
    }

    @Override
    public void saveGpuMetrics(List<GpuMetric> metrics) {
        if (metrics != null && !metrics.isEmpty()) {
            Map<String, List<GpuMetric>> byCard = new LinkedHashMap<>();

            for (GpuMetric m : metrics) {
                byCard.computeIfAbsent(m.cardNo(), k -> new ArrayList<>()).add(m);
            }

            for (Entry<String, List<GpuMetric>> e : byCard.entrySet()) {
                String table = childTable(e.getKey());
                StringBuilder sql = new StringBuilder("INSERT INTO ")
                    .append(this.database)
                    .append(".")
                    .append(table)
                    .append(" USING ")
                    .append(this.database)
                    .append(".gpu_metric TAGS (")
                    .append(this.q(e.getKey()))
                    .append(", ")
                    .append(this.q(e.getValue().get(0).hostAssetNo() == null ? "" : e.getValue().get(0).hostAssetNo()))
                    .append(") VALUES ");

                for (GpuMetric m : e.getValue()) {
                    sql.append("(")
                        .append(this.q(TS_FMT.format(m.ts())))
                        .append(", ")
                        .append(this.num(m.tempC()))
                        .append(", ")
                        .append(this.num(m.powerW()))
                        .append(", ")
                        .append(this.num(m.utilPct()))
                        .append(", ")
                        .append(m.eccDbe() == null ? "NULL" : m.eccDbe())
                        .append(", ")
                        .append(m.xidErrors() == null ? "NULL" : m.xidErrors())
                        .append(") ");
                }

                this.exec(sql.toString());
            }
        }
    }

    @Override
    public List<GpuMetric> queryWindow(String cardNo, LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT ts, temp_c, power_w, util_pct, ecc_dbe, xid_errors, host_asset_no FROM "
            + this.database
            + ".gpu_metric WHERE card_no = "
            + this.q(cardNo)
            + " AND ts >= "
            + this.q(TS_FMT.format(from))
            + " AND ts <= "
            + this.q(TS_FMT.format(to))
            + " ORDER BY ts ASC";
        return this.parseRows(this.exec(sql));
    }

    @Override
    public List<GpuMetric> recentSeries(String cardNo, int limitPoints) {
        String sql = "SELECT ts, temp_c, power_w, util_pct, ecc_dbe, xid_errors, host_asset_no FROM "
            + this.database
            + ".gpu_metric WHERE card_no = "
            + this.q(cardNo)
            + " ORDER BY ts DESC LIMIT "
            + Math.max(1, limitPoints);
        List<GpuMetric> rows = this.parseRows(this.exec(sql));
        Collections.reverse(rows);
        return rows;
    }

    static String childTable(String cardNo) {
        // 修复：非字母数字统一映射 _ 会让 A-1 / A_1 / A 1 落到同一张子表（数据互相覆盖错乱），
        // 追加 cardNo 哈希后缀保证不同 cardNo 不再碰撞。查询路径（queryWindow/recentSeries）全部
        // 按超表 + card_no TAG 过滤，不依赖子表名：存量旧子表的历史数据仍可查到，仅同一卡的
        // 新数据会写到新子表（新旧并存，对查询透明，无需迁移）。
        StringBuilder sb = new StringBuilder("dcm_");

        for (char c : cardNo.toLowerCase().toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }

        return sb.append('_').append(Integer.toHexString(cardNo.hashCode())).toString();
    }

    private List<GpuMetric> parseRows(Map<String, Object> resp) {
        List<GpuMetric> out = new ArrayList<>();
        if (resp == null) {
            return out;
        } else {
            List<?> data = resp.get("data") instanceof List<?> d ? d : null;
            if (data != null && !data.isEmpty()) {
                List<String> cols = new ArrayList<>();
                Object row = resp.get("column_meta");
                if (row instanceof List) {
                    for (Object col : (List)row) {
                        if (col instanceof List) {
                            List<?> c = (List<?>)col;
                            if (!c.isEmpty()) {
                                cols.add(String.valueOf(c.get(0)));
                            }
                        }
                    }
                }

                for (Object rowx : data) {
                    if (rowx instanceof List) {
                        List<?> cells = (List<?>)rowx;
                        Map<String, Object> r = new LinkedHashMap<>();

                        for (int i = 0; i < cells.size() && i < cols.size(); i++) {
                            r.put(cols.get(i), cells.get(i));
                        }

                        Object ts = r.get("ts");
                        if (ts != null) {
                            out.add(
                                new GpuMetric(
                                    parseTs(String.valueOf(ts)),
                                    null,
                                    this.str(r.get("host_asset_no")),
                                    this.dbl(r.get("temp_c")),
                                    this.dbl(r.get("power_w")),
                                    this.dbl(r.get("util_pct")),
                                    this.intOrNull(r.get("ecc_dbe")),
                                    this.intOrNull(r.get("xid_errors"))
                                )
                            );
                        }
                    }
                }

                return out;
            } else {
                return out;
            }
        }
    }

    private static LocalDateTime parseTs(String raw) {
        String s = raw.trim();
        if (s.endsWith("Z")) {
            s = s.substring(0, s.length() - 1);
        }

        int t = s.indexOf(84);
        int dot = s.indexOf(46, t < 0 ? 0 : t);
        if (dot >= 0) {
            String frac = s.substring(dot + 1);
            if (frac.length() > 3) {
                s = s.substring(0, dot + 1) + frac.substring(0, 3);
            }
        }

        return LocalDateTime.parse(s);
    }

    private Map<String, Object> exec(String sql) {
        Map<String, Object> resp = (Map<String, Object>)((RequestBodySpec)this.rest.post().uri("/rest/sql/" + this.database, new Object[0]))
            .body(sql)
            .retrieve()
            .body(MAP_TYPE);
        int code = resp != null && resp.get("code") instanceof Number n ? n.intValue() : -1;
        if (code != 0) {
            throw new BusinessException(500, "TDengine 执行失败：code=" + code + "，" + (resp == null ? "空响应" : resp.get("desc")));
        } else {
            return resp;
        }
    }

    private String q(String v) {
        if (v == null) {
            return "''";
        } else {
            StringBuilder sb = new StringBuilder(v.length() + 2).append('\'');

            for (char c : v.toCharArray()) {
                if (c != '\'' && c != '\\' && c >= ' ') {
                    sb.append(c);
                }
            }

            return sb.append('\'').toString();
        }
    }

    private String num(Double d) {
        return d != null && !d.isNaN() && !d.isInfinite() ? String.valueOf(d) : "NULL";
    }

    private Double dbl(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    private Integer intOrNull(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
