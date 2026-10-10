package com.dcim.system.audit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AuditRetentionService {
    
    private static final Logger log = LoggerFactory.getLogger(AuditRetentionService.class);
    private static final String PART_PREFIX = "audit_log_";
    private final JdbcTemplate jdbc;
    private final ReentrantLock lock = new ReentrantLock();
    @Value("${dcim.audit.retain-months:12}")
    private int retainMonths;
    @Value("${dcim.audit.ahead-months:3}")
    private int aheadMonths;

    public AuditRetentionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(
        cron = "${dcim.audit.retention-cron:0 40 4 * * ?}"
    )
    public void scheduled() {
        try {
            this.run();
        } catch (Exception var2) {
            log.error("操作日志保留期任务执行失败", var2);
        }
    }

    public Map<String, Object> run() {
        if (!this.lock.tryLock()) {
            return Map.of("status", "skipped", "reason", "上一次保留期清理仍在执行");
        } else {
            Object var7;
            try {
                Map<String, Object> out = new LinkedHashMap<>();
                List<String> ensured = this.jdbc
                    .queryForList("SELECT unnest(dcim_audit_ensure_partitions(?))", String.class, new Object[]{Math.max(0, this.aheadMonths)});
                out.put("ensuredPartitions", ensured);
                LocalDate cutoffMonth = LocalDate.now().withDayOfMonth(1).minusMonths((long)Math.max(1, this.retainMonths));
                LocalDateTime cutoff = cutoffMonth.atStartOfDay();
                out.put("retainMonths", this.retainMonths);
                out.put("retainBefore", cutoffMonth.toString());
                Long stale = (Long)this.jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE created_at < ?", Long.class, new Object[]{cutoff});
                if (stale == null || stale == 0L) {
                    out.put("status", "nothing-to-do");
                    out.put("deletedRows", 0);
                    out.put("droppedPartitions", List.of());
                    return out;
                }

                int deleted = this.jdbc.update("DELETE FROM audit_log WHERE created_at < ?", new Object[]{cutoff});
                out.put("deletedRows", deleted);
                if ((long)deleted != stale) {
                    log.warn("操作日志保留期删除行数与事前计数不一致：expected={} deleted={}", stale, deleted);
                }

                out.put("droppedPartitions", this.dropExpiredPartitions(cutoffMonth));
                out.put("status", "deleted");
                log.info("操作日志保留期完成：deleted={} dropped={}", deleted, out.get("droppedPartitions"));
                var7 = out;
            } finally {
                this.lock.unlock();
            }

            return (Map<String, Object>)var7;
        }
    }

    private List<String> dropExpiredPartitions(LocalDate cutoffMonth) {
        List<String> parts = this.jdbc
            .queryForList(
                "SELECT c.relname FROM pg_class c JOIN pg_inherits i ON i.inhrelid = c.oid WHERE i.inhparent = 'audit_log'::regclass AND c.relname ~ '^audit_log_[0-9]{4}_[0-9]{2}$' ORDER BY 1",
                String.class
            );
        List<String> dropped = new ArrayList<>();

        for (String part : parts) {
            String ym = part.substring("audit_log_".length());

            LocalDate month;
            try {
                month = LocalDate.parse(ym.replace('_', '-') + "-01");
            } catch (RuntimeException var9) {
                continue;
            }

            if (month.isBefore(cutoffMonth)) {
                Long n = (Long)this.jdbc.queryForObject("SELECT count(*) FROM " + part, Long.class);
                if (n != null && n > 0L) {
                    log.warn("过期分区 {} 仍有 {} 行，暂不 DROP", part, n);
                } else {
                    this.jdbc.execute("DROP TABLE IF EXISTS " + part);
                    dropped.add(part);
                }
            }
        }

        return dropped;
    }
}
