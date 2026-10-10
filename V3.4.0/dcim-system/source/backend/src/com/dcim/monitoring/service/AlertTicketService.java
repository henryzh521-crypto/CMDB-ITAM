package com.dcim.monitoring.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.BusinessException;
import com.dcim.monitoring.entity.AlertEvent;
import com.dcim.monitoring.mapper.AlertEventMapper;
import com.dcim.security.ActorContext;
import com.dcim.workorder.entity.WorkorderEntity;
import com.dcim.workorder.service.WoAssetLineInput;
import com.dcim.workorder.service.WorkorderService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AlertTicketService {
    
    private static final Logger log = LoggerFactory.getLogger(AlertTicketService.class);
    private static final List<String> OPEN_WO = List.of("待处理", "待审批", "处理中");
    private final AlertEventMapper alertMapper;
    private final WorkorderService workorderService;
    private final ActorContext actor;
    private final MonitoringSettings settings;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public AlertTicketService(
        AlertEventMapper alertMapper,
        @Lazy WorkorderService workorderService,
        ActorContext actor,
        MonitoringSettings settings,
        JdbcTemplate jdbc,
        TransactionTemplate tx
    ) {
        this.alertMapper = alertMapper;
        this.workorderService = workorderService;
        this.actor = actor;
        this.settings = settings;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    public int autoTicket() {
        String sev = this.settings.autoTicketSeverity();
        if (!"none".equals(sev) && !"off".equals(sev)) {
            int minRank = MonitoringSettings.rank(sev);
            if (minRank == 0) {
                return 0;
            } else {
                LocalDateTime cutoff = LocalDateTime.now().minusMinutes((long)this.settings.autoTicketDelayMinutes());
                List<AlertEvent> due = this.alertMapper
                    .selectList(
                        ((((((Wrappers.<AlertEvent>lambdaQuery()
                                                    .eq(AlertEvent::getStatus, "FIRING"))
                                                .eq(AlertEvent::getSuppressed, false))
                                            .isNull(AlertEvent::getWoNo))
                                        .isNull(AlertEvent::getAckBy))
                                    .le(AlertEvent::getStartsAt, cutoff))
                                .orderByAsc(AlertEvent::getStartsAt))
                            .last("LIMIT 200")
                    );
                int n = 0;

                for (AlertEvent e : due) {
                    if (MonitoringSettings.rank(e.getSeverity()) >= minRank) {
                        try {
                            this.ticket(e.getId(), "admin");
                            n++;
                        } catch (RuntimeException var9) {
                            log.warn("[alert] 告警 #{} 自动派单失败：{}", e.getId(), var9.getMessage());
                        }
                    }
                }

                return n;
            }
        } else {
            return 0;
        }
    }

    public String ticket(Long alertId, String actorUsername) {
        return (String)this.tx
            .execute(
                s -> {
                    AlertEvent e = (AlertEvent)this.alertMapper
                        .selectOne((Wrappers.<AlertEvent>lambdaQuery().eq(AlertEvent::getId, alertId)).last("FOR UPDATE"));
                    if (e == null) {
                        throw BusinessException.notFound("告警不存在");
                    } else if (e.getWoNo() != null) {
                        return e.getWoNo();
                    } else {
                        String woNo = this.existingOpenTicket(e.getAssetNo());
                        if (woNo != null) {
                            final String existingWo = woNo;
                            this.actor
                                .runAs(
                                    actorUsername,
                                    () -> this.workorderService
                                            .comment(
                                                existingWo,
                                                "【新增告警】" + e.getAlertname() + "（" + e.getSeverity() + "）：" + brief(e.getSummary()) + "，开始于 " + e.getStartsAt()
                                            )
                                );
                        } else {
                            woNo = this.actor.runAs(actorUsername, () -> this.createTicket(e).getWoNo());
                        }

                        this.alertMapper
                            .update(
                                null,
                                ((Wrappers.<AlertEvent>lambdaUpdate().eq(AlertEvent::getId, e.getId()))
                                        .set(AlertEvent::getWoNo, woNo))
                                    .set(AlertEvent::getUpdatedAt, LocalDateTime.now())
                            );
                        log.info("[alert] 告警 #{} {} → 工单 {}", new Object[]{e.getId(), e.getAlertname(), woNo});
                        return woNo;
                    }
                }
            );
    }

    private WorkorderEntity createTicket(AlertEvent e) {
        String assetName = e.getLabels() == null ? null : String.valueOf(e.getLabels().getOrDefault("asset_name", ""));
        String target = e.getAssetNo() != null ? e.getAssetNo() + (assetName != null && !assetName.isBlank() ? " " + assetName : "") : nz(e.getInstance());
        String title = "【告警】" + e.getAlertname() + " " + target;
        if (title.length() > 200) {
            title = title.substring(0, 200);
        }

        StringBuilder d = new StringBuilder();
        d.append("监控告警自动派单\n");
        d.append("告警：").append(e.getAlertname()).append("（级别 ").append(e.getSeverity()).append("）\n");
        d.append("摘要：").append(nz(e.getSummary())).append('\n');
        if (e.getDescription() != null && !e.getDescription().isBlank()) {
            d.append("详情：").append(e.getDescription()).append('\n');
        }

        d.append("对象：").append(target).append('\n');
        if (e.getRackCode() != null) {
            d.append("位置：").append(nz(e.getRoomCode())).append(" / ").append(e.getRackCode()).append('\n');
        }

        d.append("开始时间：").append(e.getStartsAt()).append('\n');
        d.append("告警编号：#").append(e.getId());
        String prio = MonitoringSettings.rank(e.getSeverity()) >= 3
            ? this.settings.get("ticket_priority_critical", "高")
            : this.settings.get("ticket_priority_warning", "中");
        List<WoAssetLineInput> lines = null;
        if (e.getAssetNo() != null) {
            WoAssetLineInput line = new WoAssetLineInput();
            line.setAssetNo(e.getAssetNo());
            lines = List.of(line);
        }

        return this.workorderService
            .create("故障维修", title, d.toString(), prio, lines, null, null, null, null, null, LocalDate.now().plusDays(1L), null, null, null, null, null);
    }

    private String existingOpenTicket(String assetNo) {
        if (assetNo == null) {
            return null;
        } else {
            List<String> rows = this.jdbc
                .queryForList(
                    "SELECT a.wo_no FROM alert_event a JOIN workorder w ON w.wo_no = a.wo_no\nWHERE a.asset_no = ? AND a.wo_no IS NOT NULL AND w.deleted_at IS NULL\n  AND w.status IN ('待处理', '待审批', '处理中')\nORDER BY a.id DESC LIMIT 1\n",
                    String.class,
                    new Object[]{assetNo}
                );
            return rows.isEmpty() ? null : rows.get(0);
        }
    }

    public void commentResolved(String woNo, AlertIngestService.Incoming in) {
        this.commentResolved(woNo, in.alertname(), in.summary());
    }

    /** 超时自动关闭等非 ingest 路径的恢复复用：按告警名/摘要给关联工单追加恢复评论 */
    public void commentResolved(String woNo, String alertname, String summary) {
        if ("true".equalsIgnoreCase(this.settings.get("resolve_comment", "true"))) {
            try {
                List<Map<String, Object>> st = this.jdbc.queryForList("SELECT status FROM workorder WHERE wo_no = ?", new Object[]{woNo});
                if (st.isEmpty() || !OPEN_WO.contains(String.valueOf(st.get(0).get("status")))) {
                    return;
                }

                this.tx
                    .executeWithoutResult(
                        s -> this.actor
                            .runAs(
                                "admin",
                                () -> this.workorderService
                                        .comment(
                                            woNo,
                                            "【告警已恢复】"
                                                + brief(alertname)
                                                + "："
                                                + brief(summary)
                                                + "，恢复时间 "
                                                + LocalDateTime.now().withNano(0)
                                                + "。请确认故障是否已彻底处理后再完结工单。"
                                        )
                            )
                    );
            } catch (RuntimeException var4) {
                log.warn("[alert] 工单 {} 追加恢复评论失败：{}", woNo, var4.getMessage());
            }
        }
    }

    private static String brief(String s) {
        String v = nz(s);
        return v.length() > 150 ? v.substring(0, 150) + "…" : v;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
