package com.dcim.monitoring.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.event.DomainEvent;
import com.dcim.common.event.DomainEventPublisher;
import com.dcim.monitoring.entity.AlertEvent;
import com.dcim.monitoring.mapper.AlertEventMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(
    name = {"dcim.monitoring.scheduler-enabled"},
    havingValue = "true",
    matchIfMissing = true
)
public class AlertScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(AlertScheduler.class);
    private final AlertTicketService tickets;
    private final JdbcTemplate jdbc;
    private final AlertEventMapper alertMapper;
    private final DomainEventPublisher events;
    private final TransactionTemplate tx;
    private final int staleHours;
    private final int retainDays;

    public AlertScheduler(
        AlertTicketService tickets,
        JdbcTemplate jdbc,
        AlertEventMapper alertMapper,
        DomainEventPublisher events,
        TransactionTemplate tx,
        @Value("${dcim.monitoring.stale-hours:12}") int staleHours,
        @Value("${dcim.monitoring.retain-days:180}") int retainDays
    ) {
        this.tickets = tickets;
        this.jdbc = jdbc;
        this.alertMapper = alertMapper;
        this.events = events;
        this.tx = tx;
        this.staleHours = Math.max(1, staleHours);
        this.retainDays = Math.max(7, retainDays);
    }

    @Scheduled(
        fixedDelayString = "${dcim.monitoring.ticket-poll-ms:60000}",
        initialDelay = 30000L
    )
    public void autoTicket() {
        try {
            int n = this.tickets.autoTicket();
            if (n > 0) {
                log.info("[alert] 本轮自动派单 {} 条告警", n);
            }
        } catch (RuntimeException var2) {
            log.warn("[alert] 自动派单异常：{}", var2.toString());
        }
    }

    @Scheduled(
        fixedDelay = 300000L,
        initialDelay = 60000L
    )
    public void expireStale() {
        // 修复：此前仅批量 UPDATE 状态，超时自动关闭的告警既不发 alert.resolved 事件、也不给关联
        // 工单追加恢复评论，与正常恢复路径（AlertIngestService.resolve + AlertTicketService.
        // commentResolved）行为不一致。现逐条关闭并补齐两件事；事务语义对齐 ingest：每条告警独立
        // 小事务（条件更新 + 发事件），工单评论在事务外由 commentResolved 自管事务，单条失败不影响其余。
        LocalDateTime cutoff = LocalDateTime.now().minusHours((long)this.staleHours);
        List<AlertEvent> stale = this.alertMapper
            .selectList(Wrappers.<AlertEvent>lambdaQuery().eq(AlertEvent::getStatus, "FIRING").lt(AlertEvent::getLastReceivedAt, cutoff));
        int n = 0;

        for (AlertEvent e : stale) {
            try {
                Boolean closed = (Boolean)this.tx
                    .execute(
                        s -> {
                            // 条件更新（仍为 FIRING 才翻转），避免与 ingest 并发恢复同一告警时
                            // 双发 alert.resolved / 工单重复评论
                            LocalDateTime now = LocalDateTime.now();
                            int updated = this.alertMapper
                                .update(
                                    null,
                                    (((((Wrappers.<AlertEvent>lambdaUpdate().eq(AlertEvent::getId, e.getId()))
                                        .eq(AlertEvent::getStatus, "FIRING"))
                                        .set(AlertEvent::getStatus, "RESOLVED"))
                                        .set(AlertEvent::getEndsAt, e.getLastReceivedAt()))
                                        .set(AlertEvent::getUpdatedAt, now))
                                        .set(AlertEvent::getCloseReason, "超时未再收到告警，系统自动关闭")
                                );
                            if (updated == 1) {
                                e.setStatus("RESOLVED");
                                e.setEndsAt(e.getLastReceivedAt());
                                if (!Boolean.TRUE.equals(e.getSuppressed())) {
                                    this.events
                                        .publish(
                                            DomainEvent.of(
                                                "alert.resolved", "alert", String.valueOf(e.getId()), "monitoring", AlertIngestService.eventData(e)
                                            )
                                        );
                                }

                                return true;
                            } else {
                                return false;
                            }
                        }
                    );
                if (Boolean.TRUE.equals(closed)) {
                    n++;
                    if (e.getWoNo() != null) {
                        this.tickets.commentResolved(e.getWoNo(), e.getAlertname(), e.getSummary());
                    }
                }
            } catch (RuntimeException var9) {
                log.warn("[alert] 超时关闭告警 #{} 失败：{}", e.getId(), var9.toString());
            }
        }

        if (n > 0) {
            log.info("[alert] {} 条告警超过 {} 小时未再收到，已自动关闭", n, this.staleHours);
        }
    }

    @Scheduled(
        cron = "${dcim.monitoring.purge-cron:0 30 4 * * ?}"
    )
    public void purge() {
        int n = this.jdbc
            .update("DELETE FROM alert_event WHERE status = 'RESOLVED' AND ends_at < NOW() - make_interval(days => ?)", new Object[]{this.retainDays});
        if (n > 0) {
            log.info("[alert] 清理 {} 天前已恢复告警 {} 条", this.retainDays, n);
        }
    }
}
