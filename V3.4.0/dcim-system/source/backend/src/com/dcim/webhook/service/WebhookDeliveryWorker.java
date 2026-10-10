package com.dcim.webhook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"dcim.webhook.worker-enabled"},
    havingValue = "true",
    matchIfMissing = true
)
public class WebhookDeliveryWorker {
    
    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryWorker.class);
    private final WebhookService webhookService;
    private final int batch;
    private final int retainDays;

    public WebhookDeliveryWorker(
        WebhookService webhookService, @Value("${dcim.webhook.batch-size:20}") int batch, @Value("${dcim.webhook.retain-days:30}") int retainDays
    ) {
        this.webhookService = webhookService;
        this.batch = Math.max(1, Math.min(batch, 200));
        this.retainDays = Math.max(1, retainDays);
    }

    @Scheduled(
        fixedDelayString = "${dcim.webhook.poll-ms:5000}",
        initialDelay = 15000L
    )
    public void tick() {
        try {
            // 一批取满说明可能还有积压，继续取；不满即本轮结束
            //（v3.2 原始字节码即为 do-while；反编译器曾误还原为死循环，此处按原语义改写）
            int n;
            do {
                n = this.webhookService.processDue(this.batch);
            } while (n == this.batch);
        } catch (RuntimeException var2) {
            log.warn("[webhook] 调度异常：{}", var2.toString());
        }
    }

    @Scheduled(
        cron = "${dcim.webhook.purge-cron:0 20 4 * * ?}"
    )
    public void purge() {
        int n = this.webhookService.purge(this.retainDays);
        if (n > 0) {
            log.info("[webhook] 清理 {} 天前投递记录 {} 条", this.retainDays, n);
        }
    }
}
