package com.dcim.collector.service;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * hw_snapshot 保留策略（v3.5.2）。
 *
 * <p>带外快照此前只增不删（每端点每个采集周期一条，含全量 JSONB 快照），长期运行会无限膨胀。
 * 每日凌晨按 collected_at 分批删除超过保留期的快照；保留期由
 * {@code dcim.collector.snapshot-retention-days} 配置（默认 180 天，≤0 表示不清理，
 * 退回 3.5.1 行为）。
 *
 * <p>分批（每批 1000 行、单晚最多 500 批）避免大事务长锁；{@code @Scheduled} 自动纳入
 * 集群互斥（ScheduledJobLockAspect），多实例部署下整段清理只在一个实例执行。
 */
@Component
public class SnapshotRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(SnapshotRetentionJob.class);
    private static final int BATCH = 1000;
    private static final int MAX_BATCHES = 500;

    private final JdbcTemplate jdbc;
    private final int retentionDays;

    public SnapshotRetentionJob(
        JdbcTemplate jdbc,
        @Value("${dcim.collector.snapshot-retention-days:180}") int retentionDays
    ) {
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 40 2 * * *")
    public void purgeExpired() {
        if (this.retentionDays <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays((long) this.retentionDays);
        long total = 0L;

        for (int i = 0; i < MAX_BATCHES; i++) {
            int n = this.jdbc.update(
                "DELETE FROM hw_snapshot WHERE id IN (SELECT id FROM hw_snapshot WHERE collected_at < ? ORDER BY id LIMIT " + BATCH + ")",
                cutoff
            );
            total += (long) n;
            if (n < BATCH) {
                break;
            }
        }

        if (total > 0L) {
            log.info("[snapshot-retention] 已清理 {} 天前的历史快照 {} 行", this.retentionDays, total);
        }
    }
}
