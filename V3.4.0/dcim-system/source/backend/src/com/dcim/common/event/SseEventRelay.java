package com.dcim.common.event;

import com.dcim.common.sse.SsePushService;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 领域事件 → SSE 频道广播（v3.5）。
 *
 * <p>事务提交后才广播（AFTER_COMMIT + fallbackExecution）：回滚的业务不外泄。
 * SSE 只承载「某频道有变化」的轻量信号，前端据此刷新列表 / 红点；通知正文走
 * NotificationAdapter 落库后的按用户推送，两者互补。
 *
 * <p><b>风暴抑制（前导 + 尾随合并）：</b>批量导入等单事务可产生上万事件，若逐条
 * 广播，事件数 × 每实例连接数即数十万次内存写。同一 {@code channel:type} 在
 * {@value #COALESCE_MS}ms 窗口内：首条立即广播（前导沿，保证低延迟），后续挂起，
 * 窗口结束补发最后一条（尾随沿，保证不漏末梢变化）。广播次数被压到常数级。
 * 尾随定时用自建守护线程 —— 禁 @Scheduled（会被集群互斥锁拦成单实例，理由见
 * SseConnectionRegistry）。
 */
@Component
public class SseEventRelay {
    private static final Logger log = LoggerFactory.getLogger(SseEventRelay.class);
    /** 只转发这些频道（DomainEvent 类型前缀）；其余（如 webhook ping）不进 SSE */
    private static final Set<String> CHANNELS = Set.of("asset", "workorder", "stock", "inventory", "alert", "change", "problem", "contract");
    private static final long COALESCE_MS = 500L;
    private final SsePushService ssePush;
    private final Map<String, Long> lastBroadcastAt = new ConcurrentHashMap<>();
    private final Map<String, DomainEvent> pendingTail = new ConcurrentHashMap<>();
    private final ScheduledExecutorService tailScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-event-coalesce");
        t.setDaemon(true);
        return t;
    });

    public SseEventRelay(SsePushService ssePush) {
        this.ssePush = ssePush;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDomainEvent(DomainEvent event) {
        try {
            String channel = channelOf(event.type());
            if (channel == null) {
                return;
            }
            String key = channel + ":" + event.type();
            long now = System.currentTimeMillis();
            Long last = this.lastBroadcastAt.get(key);
            if (last == null || now - last >= COALESCE_MS) {
                this.lastBroadcastAt.put(key, now);
                this.ssePush.broadcast(this.toPayload(event, channel));
            } else {
                // 窗口内：挂起最新一条，窗口结束补发；先到期的 flush 先 remove，后到的空转
                this.pendingTail.put(key, event);
                this.tailScheduler.schedule(() -> this.flushTail(key), COALESCE_MS - (now - last), TimeUnit.MILLISECONDS);
            }
        } catch (RuntimeException e) {
            log.warn("[sse] 事件 {} 广播失败（不影响业务）：{}", event.type(), e.toString());
        }
    }

    @PreDestroy
    void stop() {
        this.tailScheduler.shutdownNow();
    }

    private void flushTail(String key) {
        DomainEvent event = this.pendingTail.remove(key);
        if (event != null) {
            this.lastBroadcastAt.put(key, System.currentTimeMillis());
            String channel = channelOf(event.type());
            if (channel != null) {
                this.ssePush.broadcast(this.toPayload(event, channel));
            }
        }
    }

    private static String channelOf(String type) {
        if (type == null) {
            return null;
        }
        int i = type.indexOf('.');
        if (i <= 0) {
            return null;
        }
        String channel = type.substring(0, i);
        return CHANNELS.contains(channel) ? channel : null;
    }

    /**
     * SSE 频道帧载荷（D2 修复：最小化为轻量信号）。broadcast 面向本机全部连接，
     * 无法逐连接校验接收者权限，携带工单标题/申请人、库存量等业务正文会越权泄露。
     * 因此只保留前端「触发列表刷新/红点」所需的路由与定位字段，正文一律由前端
     * 自行带权限调用 REST 接口获取。注意 DomainEvent.data() 仍被 WebhookDispatcher
     * 等后端监听者消费，裁剪只发生在 SSE 出口这一处。
     */
    private Map<String, Object> toPayload(DomainEvent event, String channel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", "event");
        payload.put("channel", channel);
        payload.put("type", event.type());
        payload.put("objId", event.objId());
        payload.put("occurredAt", event.occurredAt() == null ? null : event.occurredAt().toString());
        return payload;
    }
}
