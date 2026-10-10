package com.dcim.common.sse;

import com.dcim.common.BusinessException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 连接注册表（v3.5）。
 *
 * <p>每个在线连接对应一个 {@link SseEmitter}，键为 {@code username:connId}
 * （同一用户可开多个标签页，互不干扰）。超时设 0（生命周期自管），
 * 由心跳注释帧探测死连接并就地清理。
 *
 * <p><b>为什么心跳不用 @Scheduled：</b>ScheduledJobLockAspect 会对所有 @Scheduled
 * 方法做集群互斥加锁 —— 双实例部署时只有抢到锁的实例能跑心跳，另一实例的连接
 * 会被中间层按空闲超时掐断。因此这里自建单线程守护 ScheduledExecutorService，
 * 每个实例独立心跳（各实例只对自己持有的连接负责，无需互斥）。
 *
 * <p><b>连接数上限：</b>SseEmitter 是异步长连接，占 fd 与内存，超过上限直接 503，
 * 前端 EventSource 收到错误后降级为轮询（见通知中心前端实现）。
 */
@Component
public class SseConnectionRegistry {
    private static final Logger log = LoggerFactory.getLogger(SseConnectionRegistry.class);
    /**
     * 发送超时保护池（D1 修复）：SseEmitter.send 是同步阻塞写，慢消费者 TCP 窗口
     * 打满时会把调用线程卡死在 socket 写上。心跳（单线程）与 pushUser/broadcast
     * （业务请求线程）的每一次 send 都提交到本池执行并限时等待，超时即判死连接，
     * 单个慢客户端不再冻结全实例心跳与其余连接的推送。缓存式按需建线程 + 守护 +
     * 空闲自动回收：线程数上界即连接数上限（max-connections），无需显式关闭。
     */
    private static final ExecutorService SEND_GUARD = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sse-send-guard");
        t.setDaemon(true);
        return t;
    });
    /** 单帧发送最长等待：超过即按死连接（慢消费者）处理 */
    private static final long SEND_TIMEOUT_MS = 3_000L;
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final AtomicLong connSeq = new AtomicLong();
    private final boolean enabled;
    private final int maxConnections;
    private final long heartbeatMs;
    private ScheduledExecutorService heartbeat;

    public SseConnectionRegistry(
        @Value("${dcim.sse.enabled:true}") boolean enabled,
        @Value("${dcim.sse.max-connections-per-instance:200}") int maxConnections,
        @Value("${dcim.sse.heartbeat-ms:25000}") long heartbeatMs
    ) {
        this.enabled = enabled;
        this.maxConnections = maxConnections;
        this.heartbeatMs = heartbeatMs;
    }

    @PostConstruct
    void start() {
        if (!this.enabled || this.heartbeatMs <= 0) {
            return;
        }
        this.heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
        this.heartbeat.scheduleWithFixedDelay(this::beat, this.heartbeatMs, this.heartbeatMs, TimeUnit.MILLISECONDS);
        log.info("[sse] 心跳已启动：{}ms，每实例连接上限 {}", this.heartbeatMs, this.maxConnections);
    }

    @PreDestroy
    void stop() {
        if (this.heartbeat != null) {
            this.heartbeat.shutdownNow();
        }
        for (Map.Entry<String, SseEmitter> e : this.emitters.entrySet()) {
            this.quietComplete(e.getValue());
        }
        this.emitters.clear();
    }

    /** 建立连接：注册 emitter 并立即发送 connected 帧（客户端可据此确认链路可用）。 */
    public SseEmitter register(String username) {
        if (!this.enabled) {
            throw new BusinessException(503, "SSE 推送已停用");
        } else if (this.emitters.size() >= this.maxConnections) {
            // 超限 503：前端 EventSource onerror 后降级为轮询（风险对策：fd 耗尽防护）
            throw new BusinessException(503, "SSE 连接数已达本实例上限（" + this.maxConnections + "），请稍后重试或使用轮询模式");
        }
        String key = username + ":" + this.connSeq.incrementAndGet();
        // 不能用 0（无超时）：Spring 将 0 原样传给 Tomcat 的 ASYNC_SETTIMEOUT，coyote 只对 >0 排期，
        // 0 被忽略后实际生效容器默认 30s —— async 超时 dispatch 会把 response 置为 errored，
        // 此后广播 / 心跳对该连接的写入全部失败（连接僵尸化，客户端却毫无感知）。
        // 显式 24h 兜底：真正的生命周期由 25s 心跳探测 + onTimeout/onError/onCompletion 回调清理。
        SseEmitter emitter = new SseEmitter(86_400_000L);
        emitter.onCompletion(() -> this.remove(key, emitter));
        emitter.onTimeout(() -> this.remove(key, emitter));
        emitter.onError(t -> this.remove(key, emitter));
        this.emitters.put(key, emitter);
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("ok", true)));
        } catch (RuntimeException | IOException e) {
            this.remove(key, emitter);
            throw new BusinessException(503, "SSE 连接建立失败");
        }
        log.debug("[sse] {} 上线，当前 {} 连接", key, this.emitters.size());
        return emitter;
    }

    /** 向指定用户的全部本机连接推送，返回送达数；失败的连接就地清理。 */
    public int sendToUser(String username, Object payload) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        String prefix = username + ":";
        int sent = 0;
        for (Map.Entry<String, SseEmitter> e : this.emitters.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                sent += this.send(e.getKey(), e.getValue(), payload);
            }
        }
        return sent;
    }

    /** 向本机全部连接广播，返回送达数；失败的连接就地清理。 */
    public int broadcast(Object payload) {
        int sent = 0;
        for (Map.Entry<String, SseEmitter> e : this.emitters.entrySet()) {
            sent += this.send(e.getKey(), e.getValue(), payload);
        }
        return sent;
    }

    public int count() {
        return this.emitters.size();
    }

    private int send(String key, SseEmitter emitter, Object payload) {
        try {
            this.guardedSend(key, emitter, () -> emitter.send(SseEmitter.event().name("message").data(payload)));
            return 1;
        } catch (RuntimeException | IOException e) {
            log.debug("[sse] 推送失败，移除连接 {}：{}", key, e.toString());
            this.remove(key, emitter);
            return 0;
        }
    }

    private void beat() {
        for (Map.Entry<String, SseEmitter> e : this.emitters.entrySet()) {
            try {
                // 注释帧（:ping）：只保活，不产生客户端事件；经超时保护发送，防单个慢客户端冻结心跳
                this.guardedSend(e.getKey(), e.getValue(), () -> e.getValue().send(SseEmitter.event().comment("ping")));
            } catch (RuntimeException | IOException ex) {
                this.remove(e.getKey(), e.getValue());
            }
        }
    }

    /**
     * 带超时保护的单帧发送（D1 修复）：真正的 send 在 {@code sse-send-guard} 池中执行，
     * 本线程最多等 {@value #SEND_TIMEOUT_MS}ms。返回 true 表示送达；false 表示超时或
     * 中断（超时的连接已就地清理）。send 本身抛出的 RuntimeException / IOException
     * 原样向上抛，调用方沿用既有的失败清理逻辑不变。
     */
    private boolean guardedSend(String key, SseEmitter emitter, SendAction action) throws IOException {
        // 包装为 Callable：SendAction 的受检异常经 Future 透传，解包后类型保持 IOException
        Future<?> future = SEND_GUARD.submit(() -> {
            action.run();
            return null;
        });
        try {
            future.get(SEND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            log.warn("[sse] 发送等待超过 {}ms，按死连接移除 {}（慢消费者）", SEND_TIMEOUT_MS, key);
            // 不走 remove()：其 quietComplete 会先 complete() 使 completeWithError 变成空操作，
            // 阻塞在 send 上的守护线程可能永不退出。这里直接摘表后用 completeWithError
            // 关闭 Tomcat 底层响应，阻塞的写线程会因此最终抛错退出。
            if (this.emitters.remove(key, emitter)) {
                log.debug("[sse] {} 下线，当前 {} 连接", key, this.emitters.size());
            }
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
                // 与 quietComplete 同理：complete 系列可能 sneaky-throw，必须吞掉一切异常
            }
            return false;
        } catch (InterruptedException e) {
            // 停机（shutdownNow）或调用线程被中断：不判死连接，恢复中断标记后放弃本帧
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            // 解包 send 的原始异常，交由调用方按既有清理逻辑处理
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new RuntimeException(cause);
        }
    }

    private void remove(String key, SseEmitter emitter) {
        boolean removed = this.emitters.remove(key, emitter);
        if (removed) {
            log.debug("[sse] {} 下线，当前 {} 连接", key, this.emitters.size());
        }
        this.quietComplete(emitter);
    }

    private void quietComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 必须吞掉一切异常：Spring 6.1 的 AsyncRequestNotUsableException（IOException 子类）
            // 会以 sneaky-throw 方式从 complete() 逃逸（静态签名不声明，catch RuntimeException 防不住）。
            // 若放任其逃逸：中断 broadcast 循环（其余连接收不到推送）、污染业务请求线程
            // （事务已提交却返回 500）、并杀死心跳调度线程（死连接永不清理）。
        }
    }

    /** 单帧发送动作：允许抛出 SseEmitter.send 声明的受检异常 IOException */
    @FunctionalInterface
    private interface SendAction {
        void run() throws IOException;
    }
}
