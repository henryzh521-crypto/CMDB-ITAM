package com.dcim.monitoring.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.event.DomainEvent;
import com.dcim.common.event.DomainEventPublisher;
import com.dcim.monitoring.entity.AlertEvent;
import com.dcim.monitoring.mapper.AlertEventMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AlertIngestService {
    
    private static final Logger log = LoggerFactory.getLogger(AlertIngestService.class);
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private final AlertEventMapper alertMapper;
    private final JdbcTemplate jdbc;
    private final MaintenanceMatcher maintenance;
    private final DomainEventPublisher events;
    private final TransactionTemplate tx;
    private final AlertTicketService tickets;

    public AlertIngestService(
        AlertEventMapper alertMapper,
        JdbcTemplate jdbc,
        MaintenanceMatcher maintenance,
        DomainEventPublisher events,
        TransactionTemplate tx,
        AlertTicketService tickets
    ) {
        this.alertMapper = alertMapper;
        this.jdbc = jdbc;
        this.maintenance = maintenance;
        this.events = events;
        this.tx = tx;
        this.tickets = tickets;
    }

    public AlertIngestService.Result ingestAlertmanager(Map<String, Object> payload) {
        if ((payload == null ? null : payload.get("alerts")) instanceof List<?> list) {
            ArrayList items = new ArrayList();

            for (Object o : list) {
                if (o instanceof Map<?, ?> a) {
                    Map<String, String> labels = strMap((Map<String, Object>)a.get("labels"));
                    Map<String, String> ann = strMap((Map<String, Object>)a.get("annotations"));
                    String name = labels.getOrDefault("alertname", "未命名告警");
                    String fp = str(a.get("fingerprint"));
                    items.add(
                        new AlertIngestService.Incoming(
                            fp == null ? fingerprint(labels) : fp,
                            "alertmanager",
                            name,
                            labels.getOrDefault("severity", "warning").toLowerCase(),
                            !"resolved".equalsIgnoreCase(str(a.get("status"))),
                            firstNonBlank(ann.get("summary"), ann.get("message"), name),
                            firstNonBlank(ann.get("description"), ann.get("details")),
                            labels,
                            time(str(a.get("startsAt"))),
                            time(str(a.get("endsAt"))),
                            str(a.get("generatorURL"))
                        )
                    );
                }
            }

            return this.process(items);
        } else {
            return new AlertIngestService.Result(0, 0, 0);
        }
    }

    public AlertIngestService.Result ingestGeneric(Map<String, Object> payload, String source) {
        List<Object> list = (List<Object>)((payload == null ? null : payload.get("alerts")) instanceof List<?> l
            ? l
            : (payload == null ? List.of() : List.of(payload)));
        List<AlertIngestService.Incoming> items = new ArrayList<>();

        for (Object o : list) {
            if (o instanceof Map) {
                Map<?, ?> a = (Map<?, ?>)o;
                Map<String, String> labels = strMap((Map<String, Object>)a.get("labels"));
                String name = firstNonBlank(str(a.get("name")), str(a.get("alertname")), labels.get("alertname"));
                if (name != null) {
                    labels.put("alertname", name);
                    putIfAbsent(labels, "asset_no", str(a.get("asset_no")));
                    putIfAbsent(labels, "instance", firstNonBlank(str(a.get("ip")), str(a.get("instance"))));
                    putIfAbsent(labels, "hostname", str(a.get("host")));
                    String sev = firstNonBlank(str(a.get("severity")), labels.get("severity"), "warning").toLowerCase();
                    labels.put("severity", sev);
                    String id = str(a.get("id"));
                    items.add(
                        new AlertIngestService.Incoming(
                            id != null ? sha(source + ":" + id) : fingerprint(labels),
                            source,
                            name,
                            sev,
                            !"resolved".equalsIgnoreCase(firstNonBlank(str(a.get("status")), "firing")) && !"ok".equalsIgnoreCase(str(a.get("status"))),
                            firstNonBlank(str(a.get("summary")), name),
                            str(a.get("description")),
                            labels,
                            time(str(a.get("starts_at"))),
                            time(str(a.get("ends_at"))),
                            str(a.get("url"))
                        )
                    );
                }
            }
        }

        return this.process(items);
    }

    private AlertIngestService.Result process(List<AlertIngestService.Incoming> items) {
        int f = 0;
        int r = 0;
        int ig = 0;

        for (AlertIngestService.Incoming in : items) {
            try {
                String outcome = (String)this.tx.execute(s -> in.firing() ? this.fire(in) : this.resolve(in));
                if ("FIRING".equals(outcome)) {
                    f++;
                } else if (outcome != null && outcome.startsWith("RESOLVED")) {
                    r++;
                    String woNo = outcome.length() > 9 ? outcome.substring(9) : null;
                    if (woNo != null) {
                        this.tickets.commentResolved(woNo, in);
                    }
                } else {
                    ig++;
                }
            } catch (RuntimeException var9) {
                ig++;
                log.warn("[alert] 处理告警 {} 失败：{}", in.alertname(), var9.toString());
            }
        }

        return new AlertIngestService.Result(f, r, ig);
    }

    private String fire(AlertIngestService.Incoming in) {
        LocalDateTime now = LocalDateTime.now();
        AlertEvent active = (AlertEvent)this.alertMapper
            .selectOne(
                (Wrappers.<AlertEvent>lambdaQuery().eq(AlertEvent::getFingerprint, in.fingerprint())).eq(AlertEvent::getStatus, "FIRING")
            );
        if (active != null) {
            this.alertMapper
                .update(
                    null,
                    (((((Wrappers.<AlertEvent>lambdaUpdate()
                                            .eq(AlertEvent::getId, active.getId()))
                                        .set(AlertEvent::getLastReceivedAt, now))
                                    .set(AlertEvent::getSummary, cut(in.summary(), 500)))
                                .set(AlertEvent::getDescription, in.description()))
                            .set(AlertEvent::getSeverity, in.severity()))
                        .set(AlertEvent::getUpdatedAt, now)
                );
            return "FIRING";
        } else {
            AlertIngestService.Asset asset = this.correlate(in.labels());
            AlertEvent e = new AlertEvent();
            e.setFingerprint(in.fingerprint());
            e.setSource(in.source());
            e.setAlertname(cut(in.alertname(), 200));
            e.setSeverity(in.severity());
            e.setStatus("FIRING");
            e.setSummary(cut(in.summary(), 500));
            e.setDescription(in.description());
            e.setLabels(new LinkedHashMap<>(in.labels()));
            e.setInstance(cut(in.labels().get("instance"), 200));
            e.setStartsAt(in.startsAt() == null ? now : in.startsAt());
            e.setLastReceivedAt(now);
            e.setFireCount(1);
            e.setGeneratorUrl(cut(in.generatorUrl(), 500));
            if (asset != null) {
                e.setAssetNo(asset.assetNo());
                e.setRackCode(asset.rackCode());
                e.setRoomCode(asset.roomCode());
            } else {
                e.setRackCode(in.labels().get("rack"));
                e.setRoomCode(in.labels().get("room"));
            }

            String reason = this.maintenance
                .reason(e.getAssetNo(), e.getRackCode(), e.getRoomCode(), e.getAlertname(), asset == null ? null : asset.status(), now);
            e.setSuppressed(reason != null);
            e.setSuppressReason(reason);

            try {
                this.alertMapper.insert(e);
            } catch (DuplicateKeyException var8) {
                return "FIRING";
            }

            Integer prev = (Integer)this.jdbc
                .queryForObject(
                    "SELECT COUNT(*) FROM alert_event WHERE fingerprint = ? AND status = 'RESOLVED' AND starts_at > NOW() - interval '7 days'",
                    Integer.class,
                    new Object[]{e.getFingerprint()}
                );
            if (prev != null && prev > 0) {
                this.jdbc.update("UPDATE alert_event SET fire_count = ? WHERE id = ?", new Object[]{prev + 1, e.getId()});
            }

            if (reason == null) {
                this.events.publish(DomainEvent.of("alert.firing", "alert", String.valueOf(e.getId()), "monitoring", this.eventData(e)));
            }

            return "FIRING";
        }
    }

    private String resolve(AlertIngestService.Incoming in) {
        AlertEvent active = (AlertEvent)this.alertMapper
            .selectOne(
                (Wrappers.<AlertEvent>lambdaQuery().eq(AlertEvent::getFingerprint, in.fingerprint())).eq(AlertEvent::getStatus, "FIRING")
            );
        if (active == null) {
            return "IGNORED";
        } else {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime ends = in.endsAt() != null && !in.endsAt().isAfter(now) ? in.endsAt() : now;
            this.alertMapper
                .update(
                    null,
                    ((((Wrappers.<AlertEvent>lambdaUpdate()
                                        .eq(AlertEvent::getId, active.getId()))
                                    .set(AlertEvent::getStatus, "RESOLVED"))
                                .set(AlertEvent::getEndsAt, ends))
                            .set(AlertEvent::getLastReceivedAt, now))
                        .set(AlertEvent::getUpdatedAt, now)
                );
            active.setEndsAt(ends);
            active.setStatus("RESOLVED");
            if (!Boolean.TRUE.equals(active.getSuppressed())) {
                this.events.publish(DomainEvent.of("alert.resolved", "alert", String.valueOf(active.getId()), "monitoring", this.eventData(active)));
            }

            return active.getWoNo() == null ? "RESOLVED" : "RESOLVED:" + active.getWoNo();
        }
    }

    // 包级可见 + static：超时自动关闭路径（AlertScheduler.expireStale）复用同一事件数据结构，
    // 保证 alert.resolved 事件载荷与正常恢复路径完全一致
    static Map<String, Object> eventData(AlertEvent e) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("alertId", e.getId());
        d.put("alertname", e.getAlertname());
        d.put("severity", e.getSeverity());
        d.put("status", e.getStatus());
        d.put("summary", e.getSummary());
        d.put("assetNo", e.getAssetNo());
        d.put("assetName", e.getLabels() == null ? null : e.getLabels().get("asset_name"));
        d.put("instance", e.getInstance());
        d.put("rack", e.getRackCode());
        d.put("room", e.getRoomCode());
        d.put("startsAt", String.valueOf(e.getStartsAt()));
        if (e.getEndsAt() != null) {
            d.put("endsAt", String.valueOf(e.getEndsAt()));
        }

        return d;
    }

    AlertIngestService.Asset correlate(Map<String, String> labels) {
        String assetNo = labels.get("asset_no");
        if (assetNo != null && !assetNo.isBlank()) {
            AlertIngestService.Asset a = this.one(
                "SELECT asset_no, rack_code, room_code, status_biz FROM asset WHERE asset_no = ? AND deleted_at IS NULL", assetNo.trim()
            );
            if (a != null) {
                return a;
            }
        }

        String ip = hostOf(labels.get("instance"));
        if (ip == null) {
            ip = hostOf(labels.get("target"));
        }

        if (ip != null) {
            AlertIngestService.Asset a = this.one(
                "SELECT asset_no, rack_code, room_code, status_biz FROM asset WHERE deleted_at IS NULL AND (TRIM(biz_ip) = ? OR TRIM(mgmt_ip) = ?) ORDER BY (TRIM(biz_ip) = ?) DESC LIMIT 1",
                ip,
                ip,
                ip
            );
            if (a != null) {
                return a;
            }
        }

        String host = labels.getOrDefault("hostname", labels.get("nodename"));
        return host != null && !host.isBlank()
            ? this.one(
                "SELECT asset_no, rack_code, room_code, status_biz FROM asset WHERE deleted_at IS NULL AND (name = ? OR UPPER(sn) = UPPER(?)) LIMIT 1",
                host.trim(),
                host.trim()
            )
            : null;
    }

    private AlertIngestService.Asset one(String sql, Object... args) {
        List<AlertIngestService.Asset> list = this.jdbc
            .query(sql, (rs, i) -> new AlertIngestService.Asset(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), args);
        return list.isEmpty() ? null : list.get(0);
    }

    static String hostOf(String instance) {
        if (instance != null && !instance.isBlank()) {
            String s = instance.trim().replaceFirst("^[a-zA-Z]+://", "");
            int slash = s.indexOf(47);
            if (slash >= 0) {
                s = s.substring(0, slash);
            }

            if (s.startsWith("[")) {
                int end = s.indexOf(93);
                return end > 0 ? s.substring(1, end) : s;
            } else {
                int colon = s.indexOf(58);
                if (colon > 0 && s.indexOf(58, colon + 1) < 0) {
                    s = s.substring(0, colon);
                }

                return s;
            }
        } else {
            return null;
        }
    }

    static String fingerprint(Map<String, String> labels) {
        return sha(new TreeMap<>(labels).toString()).substring(0, 32);
    }

    private static String sha(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception var2) {
            throw new IllegalStateException(var2);
        }
    }

    static LocalDateTime time(String s) {
        if (s != null && !s.isBlank() && !s.startsWith("0001-")) {
            try {
                return OffsetDateTime.parse(s).atZoneSameInstant(ZONE).toLocalDateTime();
            } catch (DateTimeParseException var4) {
                try {
                    return LocalDateTime.parse(s.replace(' ', 'T'));
                } catch (DateTimeParseException var3) {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    private static Map<String, String> strMap(Map<String, Object> m) {
        Map<String, String> out = new LinkedHashMap<>();
        if (m != null) {
            m.forEach((k, v) -> {
                if (k != null && v != null) {
                    out.put(k, String.valueOf(v));
                }
            });
        }

        return out;
    }

    private static String str(Object o) {
        return o != null && !String.valueOf(o).isBlank() ? String.valueOf(o) : null;
    }

    private static void putIfAbsent(Map<String, String> m, String k, String v) {
        if (v != null && !m.containsKey(k)) {
            m.put(k, v);
        }
    }

    private static String firstNonBlank(String... vs) {
        for (String v : vs) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }

        return null;
    }

    private static String cut(String s, int n) {
        return s != null && s.length() > n ? s.substring(0, n) : s;
    }

    static record Asset(String assetNo, String rackCode, String roomCode, String status) {
    }

    public static record Incoming(
        String fingerprint,
        String source,
        String alertname,
        String severity,
        boolean firing,
        String summary,
        String description,
        Map<String, String> labels,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String generatorUrl
    ) {
    }

    public static record Result(int firing, int resolved, int ignored) {
    }
}
