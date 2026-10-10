package com.dcim.common.cluster;

import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 给所有 {@code @Scheduled} 方法加集群互斥（v3.3）。
 *
 * <p>v3.2 有 12 个定时任务（SLA 升级、告警自动派单、巡检生成、Webhook 投递、折旧计提……），
 * 均无分布式锁；后端扩到 2 个及以上实例时会重复执行（重复升级通知、重复派单、重复投递）。
 * 现在同一任务同一时刻只会在一个实例上运行，单实例部署下行为不变。
 * v3.4：执行完把锁保持到「开始时刻 + 周期 × 0.9」，同一周期内其它实例的触发跳过（v3.3 只防并发、不防同周期重复）。
 */
@Aspect
@Component
@Order(0)
public class ScheduledJobLockAspect {
    private static final Logger log = LoggerFactory.getLogger(ScheduledJobLockAspect.class);
    private final ClusterJobLock lock;
    private final org.springframework.core.env.Environment env;
    private final long maxLeaseSeconds;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> holdMs = new java.util.concurrent.ConcurrentHashMap<>();
    /** cron 任务的保持期上限：各实例按同一 cron 几乎同时触发，10 分钟足以让慢一步的实例跳过 */
    private static final long CRON_HOLD_CAP_MS = 600_000L;

    public ScheduledJobLockAspect(
        ClusterJobLock lock,
        org.springframework.core.env.Environment env,
        @org.springframework.beans.factory.annotation.Value("${dcim.scheduler.cluster-lock.max-lease-seconds:1800}") long maxLeaseSeconds
    ) {
        this.lock = lock;
        this.env = env;
        this.maxLeaseSeconds = Math.max(0L, maxLeaseSeconds);
    }

    /** 保持期 = 周期 × 0.9（cron 取相邻两次触发的间隔，且不超过 10 分钟） */
    long holdFor(String name, org.springframework.scheduling.annotation.Scheduled s) {
        return this.holdMs.computeIfAbsent(name, k -> {
            try {
                long period = 0L;
                if (s.fixedDelay() > 0) {
                    period = s.fixedDelay();
                } else if (s.fixedRate() > 0) {
                    period = s.fixedRate();
                } else if (!s.fixedDelayString().isEmpty()) {
                    period = java.time.Duration.parse(normalize(this.env.resolvePlaceholders(s.fixedDelayString()))).toMillis();
                } else if (!s.fixedRateString().isEmpty()) {
                    period = java.time.Duration.parse(normalize(this.env.resolvePlaceholders(s.fixedRateString()))).toMillis();
                } else if (!s.cron().isEmpty()) {
                    org.springframework.scheduling.support.CronExpression cron =
                        org.springframework.scheduling.support.CronExpression.parse(this.env.resolvePlaceholders(s.cron()));
                    java.time.LocalDateTime a = cron.next(java.time.LocalDateTime.now());
                    java.time.LocalDateTime b = a == null ? null : cron.next(a);
                    if (a != null && b != null) {
                        period = Math.min(java.time.Duration.between(a, b).toMillis(), CRON_HOLD_CAP_MS * 10 / 9);
                    }
                }
                return Math.max(0L, period * 9 / 10);
            } catch (RuntimeException e) {
                log.debug("[cluster-lock] 无法推算 {} 的执行周期：{}", name, e.getMessage());
                return 0L;
            }
        });
    }

    /** "5000" → "PT5S"（毫秒数）；已是 ISO-8601 时原样返回 */
    private static String normalize(String v) {
        String t = v.trim();
        return t.matches("\\d+") ? "PT" + (Long.parseLong(t) / 1000.0) + "S" : t;
    }

    /** 心跳续租线程（守护线程，首次长任务时懒创建） */
    private volatile java.util.concurrent.ScheduledExecutorService heartbeats;

    private java.util.concurrent.ScheduledExecutorService heartbeats() {
        if (this.heartbeats == null) {
            synchronized (this) {
                if (this.heartbeats == null) {
                    this.heartbeats = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "cluster-lock-heartbeat");
                        t.setDaemon(true);
                        return t;
                    });
                }
            }
        }
        return this.heartbeats;
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        if (!this.lock.enabled()) {
            return pjp.proceed();
        }
        Method m = ((MethodSignature) pjp.getSignature()).getMethod();
        Class<?> target = AopUtils.getTargetClass(pjp.getTarget());
        String name = target.getSimpleName() + "." + m.getName();
        if (!this.lock.tryAcquire(name, 0L)) {
            log.debug("[cluster-lock] {} 正由其它实例执行，本轮跳过", name);
            return null;
        }
        long started = System.currentTimeMillis();
        long hold = this.holdFor(name, m.getAnnotation(org.springframework.scheduling.annotation.Scheduled.class));
        // 修复：长任务（运行超过默认租约 600s）此前会在运行中途租约到期，被其它实例抢锁并发执行。
        // 运行期间以 租约/3 的间隔心跳续租，任务结束后停止。
        // v3.5.2：续租带上限（默认 1800s，dcim.scheduler.cluster-lock.max-lease-seconds 可调）。
        // 心跳无限续租会让挂死 / 死循环的任务永久独占锁（v3.5.1 回归实测残留锁阻塞 45 分钟）；
        // 达到上限后停止续租并告警，租约自然过期，其它实例可接管。
        long lease = this.lock.leaseSeconds();
        java.util.concurrent.ScheduledFuture<?> hb = null;
        if (lease > 0L) {
            long interval = Math.max(5L, lease / 3);
            final long capMs = started + Math.max(lease, this.maxLeaseSeconds) * 1000L;
            final String jobName = name;
            hb = this.heartbeats().scheduleWithFixedDelay(() -> {
                if (System.currentTimeMillis() >= capMs) {
                    log.warn("[cluster-lock] {} 执行时间超过租约上限 {}s，停止续租（锁到期后其它实例可接管；请检查该任务是否挂死）",
                        jobName, Math.max(lease, ScheduledJobLockAspect.this.maxLeaseSeconds));
                    // 抛出异常终止本任务的后续心跳（ScheduledExecutorService 周期任务语义）
                    throw new IllegalStateException("cluster-lock lease cap reached: " + jobName);
                }
                this.lock.renewCapped(jobName, lease, started, Math.max(lease, this.maxLeaseSeconds));
            }, interval, interval, java.util.concurrent.TimeUnit.SECONDS);
        }
        try {
            return pjp.proceed();
        } finally {
            if (hb != null) {
                hb.cancel(false);
            }
            if (hold > 0L) {
                this.lock.holdUntil(name, started + hold);
            } else {
                this.lock.release(name);
            }
        }
    }
}
