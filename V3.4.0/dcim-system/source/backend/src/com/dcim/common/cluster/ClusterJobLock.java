package com.dcim.common.cluster;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 集群定时任务互斥锁（ShedLock 语义，基于 PostgreSQL 表 job_lock）。
 *
 * <p>抢锁是一条带条件的 UPSERT：只有租约已过期（或从未持有）时才能写入成功，天然原子；
 * 不占用数据库连接、不依赖会话级 advisory lock，实例崩溃后租约到期即可被其它实例接管。
 */
@Component
public class ClusterJobLock {
    private static final Logger log = LoggerFactory.getLogger(ClusterJobLock.class);
    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final long defaultLeaseSeconds;
    private final String instanceId;

    public ClusterJobLock(
        JdbcTemplate jdbc,
        @Value("${dcim.scheduler.cluster-lock.enabled:true}") boolean enabled,
        @Value("${dcim.scheduler.cluster-lock.lease-seconds:600}") long defaultLeaseSeconds
    ) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.defaultLeaseSeconds = Math.max(10L, defaultLeaseSeconds);
        this.instanceId = resolveInstanceId();
    }

    public boolean enabled() {
        return this.enabled;
    }

    /** 默认租约秒数（供心跳续租间隔计算） */
    public long leaseSeconds() {
        return this.defaultLeaseSeconds;
    }

    public String instanceId() {
        return this.instanceId;
    }

    /** 尝试获取锁；拿到返回 true。数据库异常时返回 false（宁可本轮跳过，也不重复执行）。 */
    public boolean tryAcquire(String name, long leaseSeconds) {
        long lease = leaseSeconds > 0 ? leaseSeconds : this.defaultLeaseSeconds;
        try {
            int n = this.jdbc.update(
                "INSERT INTO job_lock (name, locked_until, locked_at, locked_by, run_count) VALUES (?, clock_timestamp() + make_interval(secs => ?), clock_timestamp(), ?, 1) "
                    + "ON CONFLICT (name) DO UPDATE SET locked_until = EXCLUDED.locked_until, locked_at = EXCLUDED.locked_at, locked_by = EXCLUDED.locked_by, "
                    + "run_count = job_lock.run_count + 1 "
                    + "WHERE job_lock.locked_until <= clock_timestamp()",
                name, (double) lease, this.instanceId
            );
            return n == 1;
        } catch (RuntimeException e) {
            log.warn("[cluster-lock] 获取任务锁失败 {}：{}", name, e.getMessage());
            return false;
        }
    }

    public void release(String name) {
        try {
            this.jdbc.update("UPDATE job_lock SET locked_until = clock_timestamp() WHERE name = ? AND locked_by = ?", name, this.instanceId);
        } catch (RuntimeException e) {
            log.warn("[cluster-lock] 释放任务锁失败 {}：{}（租约到期后自动释放）", name, e.getMessage());
        }
    }

    /**
     * v3.5.2：带租约上限的心跳续租。心跳无限续租会让死循环 / 挂死的任务永久独占锁
     * （v3.5.1 回归中 HealthScoreService 被已崩溃实例的残留锁阻塞约 45 分钟）。
     * 续租后的到期时间不超过「抢锁时刻 + maxLeaseSeconds」；达到上限后调用方应停止续租，
     * 租约自然过期，其它实例即可接管。
     */
    public void renewCapped(String name, long leaseSeconds, long acquireEpochMs, long maxLeaseSeconds) {
        long lease = Math.max(10L, leaseSeconds);
        long cap = Math.max(lease, maxLeaseSeconds);
        try {
            this.jdbc.update(
                "UPDATE job_lock SET locked_until = LEAST(clock_timestamp() + make_interval(secs => ?), to_timestamp(? / 1000.0) + make_interval(secs => ?)) WHERE name = ? AND locked_by = ?",
                (double) lease, (double) acquireEpochMs, (double) cap, name, this.instanceId
            );
        } catch (RuntimeException e) {
            log.warn("[cluster-lock] 续租失败 {}：{}（若锁已被其它实例接管，本实例任务完成后无法释放）", name, e.getMessage());
        }
    }

    /**
     * v3.4：执行完不立即释放，而是把租约保持到 {@code untilEpochMs}（至少到当前时刻）。
     * 同一周期内其它实例的触发因此会跳过——v3.3 执行完即释放，只防住了「同时执行」，
     * 两个实例的触发时刻错开时同一任务每个周期会各跑一次（ShedLock 的 lockAtLeastFor 语义）。
     */
    public void holdUntil(String name, long untilEpochMs) {
        try {
            this.jdbc.update(
                "UPDATE job_lock SET locked_until = GREATEST(clock_timestamp(), to_timestamp(? / 1000.0)) WHERE name = ? AND locked_by = ?",
                (double) untilEpochMs, name, this.instanceId
            );
        } catch (RuntimeException e) {
            log.warn("[cluster-lock] 设置任务锁保持期失败 {}：{}（租约到期后自动释放）", name, e.getMessage());
        }
    }

    private static String resolveInstanceId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        String pid = ManagementFactory.getRuntimeMXBean().getName();
        int at = pid.indexOf('@');
        if (at > 0) {
            pid = pid.substring(0, at);
        }
        String id = host + ":" + pid + ":" + UUID.randomUUID().toString().substring(0, 8);
        return id.length() > 250 ? id.substring(0, 250) : id;
    }
}
