package com.dcim.inspection.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"dcim.inspection.scheduler-enabled"},
    havingValue = "true",
    matchIfMissing = true
)
public class InspectionScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(InspectionScheduler.class);
    private final InspectionService service;

    public InspectionScheduler(InspectionService service) {
        this.service = service;
    }

    @Scheduled(
        cron = "${dcim.inspection.cron:0 * * * * ?}"
    )
    public void tick() {
        try {
            int n = this.service.runDue();
            if (n > 0) {
                log.info("[inspection] 本轮生成巡检工单 {} 张", n);
            }
        } catch (RuntimeException var2) {
            log.warn("[inspection] 调度异常：{}", var2.toString());
        }
    }
}
