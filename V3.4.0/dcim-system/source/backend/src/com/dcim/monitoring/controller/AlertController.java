package com.dcim.monitoring.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.monitoring.entity.AlertEvent;
import com.dcim.monitoring.mapper.AlertEventMapper;
import com.dcim.monitoring.service.AlertTicketService;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/alerts"})
public class AlertController {
    private final AlertEventMapper alertMapper;
    private final AlertTicketService tickets;
    private final JdbcTemplate jdbc;

    @GetMapping
    @RequirePerm(
        module = "monitoring"
    )
    public ApiResult<PageResult<AlertEvent>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String assetNo,
        @RequestParam(required = false) Boolean suppressed,
        @RequestParam(required = false) Boolean acked
    ) {
        LambdaQueryWrapper<AlertEvent> q = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) {
            q.eq(AlertEvent::getStatus, status.trim().toUpperCase());
        }

        if (severity != null && !severity.isBlank()) {
            q.eq(AlertEvent::getSeverity, severity.trim().toLowerCase());
        }

        if (assetNo != null && !assetNo.isBlank()) {
            q.eq(AlertEvent::getAssetNo, assetNo.trim());
        }

        if (suppressed != null) {
            q.eq(AlertEvent::getSuppressed, suppressed);
        }

        if (acked != null) {
            if (acked) {
                q.isNotNull(AlertEvent::getAckBy);
            } else {
                q.isNull(AlertEvent::getAckBy);
            }
        }

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(
                w -> ((((((w.like(
                                                AlertEvent::getAlertname, kw
                                            ))
                                            .or())
                                        .like(AlertEvent::getSummary, kw))
                                    .or())
                                .like(AlertEvent::getAssetNo, kw))
                            .or())
                        .like(AlertEvent::getInstance, kw)
            );
        }

        ((q.orderByAsc(AlertEvent::getStatus)).orderByDesc(AlertEvent::getStartsAt)).orderByDesc(AlertEvent::getId);
        Page<AlertEvent> p = (Page<AlertEvent>)this.alertMapper.selectPage(Page.of(page, Math.min(size, 200L)), q);
        return ApiResult.ok(PageResult.of(p));
    }

    @GetMapping({"/{id}"})
    @RequirePerm(
        module = "monitoring"
    )
    public ApiResult<Map<String, Object>> detail(@PathVariable Long id) {
        AlertEvent e = this.require(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("alert", e);
        out.put(
            "history",
            this.jdbc
                .queryForList(
                    "SELECT id, status, starts_at AS \"startsAt\", ends_at AS \"endsAt\", wo_no AS \"woNo\", suppressed\nFROM alert_event WHERE fingerprint = ? ORDER BY starts_at DESC LIMIT 20\n",
                    new Object[]{e.getFingerprint()}
                )
        );
        if (e.getAssetNo() != null) {
            out.put(
                "related",
                this.jdbc
                    .queryForList(
                        "SELECT id, alertname, severity, status, starts_at AS \"startsAt\"\nFROM alert_event WHERE asset_no = ? AND id <> ? AND starts_at > NOW() - interval '7 days'\nORDER BY starts_at DESC LIMIT 20\n",
                        new Object[]{e.getAssetNo(), id}
                    )
            );
        }

        return ApiResult.ok(out);
    }

    @GetMapping({"/stats"})
    @RequirePerm(
        module = "monitoring"
    )
    public ApiResult<Map<String, Object>> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Long> bySev = new LinkedHashMap<>();

        for (String s : List.of("critical", "warning", "info")) {
            bySev.put(s, 0L);
        }

        this.jdbc
            .queryForList("SELECT severity, COUNT(*) AS n FROM alert_event WHERE status = 'FIRING' AND NOT suppressed GROUP BY 1")
            .forEach(r -> bySev.put(String.valueOf(r.get("severity")), ((Number)r.get("n")).longValue()));
        out.put("firingBySeverity", bySev);
        out.put("suppressed", this.jdbc.queryForObject("SELECT COUNT(*) FROM alert_event WHERE status = 'FIRING' AND suppressed", Long.class));
        out.put(
            "unacked", this.jdbc.queryForObject("SELECT COUNT(*) FROM alert_event WHERE status = 'FIRING' AND NOT suppressed AND ack_by IS NULL", Long.class)
        );
        out.put("new24h", this.jdbc.queryForObject("SELECT COUNT(*) FROM alert_event WHERE starts_at > NOW() - interval '24 hours'", Long.class));
        out.put(
            "mttrMinutes",
            this.jdbc
                .queryForObject(
                    "SELECT ROUND(COALESCE(AVG(EXTRACT(EPOCH FROM (ends_at - starts_at)) / 60), 0)::numeric, 1)\nFROM alert_event WHERE status = 'RESOLVED' AND ends_at > NOW() - interval '30 days'\n",
                    Double.class
                )
        );
        out.put(
            "topAssets",
            this.jdbc
                .queryForList(
                    "SELECT a.asset_no AS \"assetNo\", s.name AS \"assetName\", COUNT(*) AS n\nFROM alert_event a LEFT JOIN asset s ON s.asset_no = a.asset_no\nWHERE a.asset_no IS NOT NULL AND a.starts_at > NOW() - interval '7 days'\nGROUP BY a.asset_no, s.name ORDER BY n DESC LIMIT 10\n"
                )
        );
        out.put(
            "topAlerts",
            this.jdbc
                .queryForList(
                    "SELECT alertname, COUNT(*) AS n FROM alert_event WHERE starts_at > NOW() - interval '7 days'\nGROUP BY alertname ORDER BY n DESC LIMIT 10\n"
                )
        );
        out.put(
            "daily",
            this.jdbc
                .queryForList(
                    "SELECT to_char(starts_at::date, 'MM-DD') AS day, severity, COUNT(*) AS n FROM alert_event\nWHERE starts_at > CURRENT_DATE - 6 GROUP BY 1, 2 ORDER BY 1\n"
                )
        );
        return ApiResult.ok(out);
    }

    @PostMapping({"/{id}/ack"})
    @RequirePerm(
        module = "monitoring",
        write = true
    )
    @Audit(
        action = "ACK",
        module = "monitoring",
        objType = "alert",
        objIdSpEL = "#id"
    )
    public ApiResult<AlertEvent> ack(@PathVariable Long id, @RequestBody(required = false) AlertController.NoteReq req) {
        LoginUser u = requireUser();
        AlertEvent e = this.require(id);
        if (e.getAckBy() != null) {
            throw BusinessException.conflict("告警已被 " + e.getAckBy() + " 确认");
        } else {
            this.alertMapper
                .update(
                    null,
                    ((((Wrappers.<AlertEvent>lambdaUpdate()
                                        .eq(AlertEvent::getId, id))
                                    .set(AlertEvent::getAckBy, u.username()))
                                .set(AlertEvent::getAckAt, LocalDateTime.now()))
                            .set(AlertEvent::getAckNote, req == null ? null : req.note()))
                        .set(AlertEvent::getUpdatedAt, LocalDateTime.now())
                );
            return ApiResult.ok(this.require(id));
        }
    }

    @PostMapping({"/ack-batch"})
    @RequirePerm(
        module = "monitoring",
        write = true
    )
    @Audit(
        action = "ACK",
        module = "monitoring",
        objType = "alert"
    )
    public ApiResult<Map<String, Object>> ackBatch(@RequestBody AlertController.BatchReq req) {
        LoginUser u = requireUser();
        if (req != null && req.ids() != null && !req.ids().isEmpty() && req.ids().size() <= 500) {
            int n = this.alertMapper
                .update(
                    null,
                    (((((Wrappers.<AlertEvent>lambdaUpdate()
                                            .in(AlertEvent::getId, req.ids()))
                                        .isNull(AlertEvent::getAckBy))
                                    .set(AlertEvent::getAckBy, u.username()))
                                .set(AlertEvent::getAckAt, LocalDateTime.now()))
                            .set(AlertEvent::getAckNote, req.note()))
                        .set(AlertEvent::getUpdatedAt, LocalDateTime.now())
                );
            return ApiResult.ok(Map.of("acked", n));
        } else {
            throw BusinessException.badRequest("请选择 1-500 条告警");
        }
    }

    @PostMapping({"/{id}/ticket"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "create"
    )
    @Audit(
        action = "TICKET",
        module = "monitoring",
        objType = "alert",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> ticket(@PathVariable Long id) {
        LoginUser u = requireUser();
        this.require(id);
        return ApiResult.ok(Map.of("woNo", this.tickets.ticket(id, u.username())));
    }

    private AlertEvent require(Long id) {
        AlertEvent e = (AlertEvent)this.alertMapper.selectById(id);
        if (e == null) {
            throw BusinessException.notFound("告警不存在");
        } else {
            return e;
        }
    }

    private static LoginUser requireUser() {
        LoginUser u = CurrentUser.get();
        if (u == null) {
            throw BusinessException.unauthorized("未登录或会话已过期");
        } else {
            return u;
        }
    }

    
    public AlertController(final AlertEventMapper alertMapper, final AlertTicketService tickets, final JdbcTemplate jdbc) {
        this.alertMapper = alertMapper;
        this.tickets = tickets;
        this.jdbc = jdbc;
    }

    public static record BatchReq(List<Long> ids, @Size(max = 500) String note) {
    }

    public static record NoteReq(@Size(max = 500) String note) {
    }
}
