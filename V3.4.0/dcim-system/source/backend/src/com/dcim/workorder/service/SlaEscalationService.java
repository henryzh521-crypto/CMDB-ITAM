package com.dcim.workorder.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.event.DomainEvent;
import com.dcim.common.event.EventTypes;
import com.dcim.common.event.UnifiedEventPublisher;
import com.dcim.common.notify.NotificationPort;
import com.dcim.workorder.entity.SlaConfigEntity;
import com.dcim.workorder.entity.WorkorderEntity;
import com.dcim.workorder.mapper.SlaConfigMapper;
import com.dcim.workorder.mapper.WorkorderMapper;
import com.dcim.workorder.sla.SlaPolicy;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SlaEscalationService {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationService.class);
    static final long THROTTLE_MINUTES = 60L;
    static final int MAX_ESCALATIONS = 5;
    static final String NOTIFY_TYPE = "SLA";
    private final WorkorderMapper workorderMapper;
    private final SlaConfigMapper slaConfigMapper;
    private final NotificationPort notificationPort;
    private final UnifiedEventPublisher events;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;

    public static LocalDateTime computeDeadline(LocalDateTime base, Integer minutes) {
        return base != null && minutes != null && minutes >= 0 ? base.plusMinutes((long)minutes.intValue()) : null;
    }

    @Scheduled(
        fixedDelay = 60000L
    )
    public void scheduledRun() {
        try {
            int n = this.runOnce();
            if (n > 0) {
                log.info("[sla] 本轮升级 {} 张超期工单", n);
            }
        } catch (Exception var2) {
            log.warn("[sla] SLA 升级扫描失败（不影响下次调度）: {}", var2.getMessage(), var2);
        }
    }

    /** 修复：自调用会绕过 Spring 代理导致 @Transactional 失效，改用 TransactionTemplate 显式声明事务 */
    public int runOnce() {
        Integer escalated = this.transactionTemplate.execute(status -> this.doRunOnce());
        return escalated == null ? 0 : escalated;
    }

    private int doRunOnce() {
        LocalDateTime now = LocalDateTime.now();
        List<WorkorderEntity> overdue = this.workorderMapper
            .selectList(
                (((((Wrappers.<WorkorderEntity>lambdaQuery()
                                        .notIn(WorkorderEntity::getStatus, SlaPolicy.SLA_EXCLUDED_STATES))
                                    .isNotNull(WorkorderEntity::getSlaResolveDeadline))
                                .le(WorkorderEntity::getSlaResolveDeadline, now))
                            // 修复：SQL 中 sla_escalations < 5 会排除 NULL 行，存量数据为 NULL 的工单永远不会被升级
                            .and(w -> ((w.isNull(WorkorderEntity::getSlaEscalations)).or())
                                    .lt(WorkorderEntity::getSlaEscalations, 5)))
                        .and(
                            w -> ((w.isNull(WorkorderEntity::getLastEscalationAt)).or())
                                    .le(WorkorderEntity::getLastEscalationAt, now.minusMinutes(60L))
                        ))
                    .last("FOR UPDATE")
            );
        int escalated = 0;

        for (WorkorderEntity wo : overdue) {
            // 修复：sla_escalations 为 NULL 时 "sla_escalations = NULL" 在 SQL 中永假，CAS 永远不生效；
            // 统一用 COALESCE 兜底
            int current = wo.getSlaEscalations() == null ? 0 : wo.getSlaEscalations();
            int updated = this.jdbc
                .update(
                    "UPDATE workorder SET sla_escalations = COALESCE(sla_escalations, 0) + 1, last_escalation_at = ? WHERE wo_no = ? AND COALESCE(sla_escalations, 0) = ? AND COALESCE(sla_escalations, 0) < ?",
                    new Object[]{now, wo.getWoNo(), current, 5}
                );
            if (updated != 0) {
                escalated++;
                int times = current + 1;
                String title = "工单 SLA 超期升级（第 " + times + " 次）";
                String content = "工单 " + wo.getWoNo() + "「" + wo.getTitle() + "」已超过 SLA 解决时限（" + wo.getSlaResolveDeadline() + "），仍未完成，请尽快处理";
                this.notificationPort.notifyUser(wo.getHandler(), "SLA", title, content, wo.getWoNo());
                this.notificationPort.notifyUser(wo.getApplicantUsername(), "SLA", title, content, wo.getWoNo());
                SlaConfigEntity cfg = (SlaConfigEntity)this.slaConfigMapper
                    .selectOne(
                        (Wrappers.<SlaConfigEntity>lambdaQuery().eq(SlaConfigEntity::getWoType, wo.getWoType()))
                            .eq(SlaConfigEntity::getPriority, wo.getPriority())
                    );
                if (cfg != null && cfg.getEscalateTo() != null && !cfg.getEscalateTo().isBlank()) {
                    this.notificationPort.notifyRole(cfg.getEscalateTo().trim(), "SLA", title, content, wo.getWoNo());
                }
                // v3.5：补发领域事件（webhook 发件箱 + SSE 广播）；此处处于 runOnce 的事务内，
                // BEFORE_COMMIT 入发件箱、AFTER_COMMIT 才广播，站内信保持上面不动
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("woNo", wo.getWoNo());
                data.put("title", wo.getTitle());
                data.put("woType", wo.getWoType());
                data.put("priority", wo.getPriority());
                data.put("times", times);
                data.put("deadline", wo.getSlaResolveDeadline() == null ? null : wo.getSlaResolveDeadline().toString());
                data.put("handler", wo.getHandler());
                this.events.publish(DomainEvent.of(EventTypes.WORKORDER_SLA_ESCALATED, "workorder", wo.getWoNo(), "system", data));
            }
        }

        return escalated;
    }

    
    public SlaEscalationService(
        final WorkorderMapper workorderMapper, final SlaConfigMapper slaConfigMapper, final NotificationPort notificationPort, final UnifiedEventPublisher events,
        final JdbcTemplate jdbc,
        final TransactionTemplate transactionTemplate
    ) {
        this.workorderMapper = workorderMapper;
        this.slaConfigMapper = slaConfigMapper;
        this.notificationPort = notificationPort;
        this.events = events;
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
    }
}
