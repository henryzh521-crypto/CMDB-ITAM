package com.dcim.warehouse.service;

import com.dcim.common.notify.NotificationPort;
import com.dcim.security.ActorContext;
import com.dcim.workorder.entity.WorkorderEntity;
import com.dcim.workorder.service.WorkorderService;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 低库存联动（v3.5）。
 *
 * <p>{@code WebhookDispatcher.checkLowStock} 在业务事务 BEFORE_COMMIT 阶段命中
 * 「stock &lt; safety」时回调 {@link #onLowStock}。本服务把联动工作推迟到
 * <b>事务提交后</b> 的单线程守护任务里（尽力而为，任何失败只记日志，绝不影响出入库）：
 * <ul>
 *   <li><b>off</b> —— 不联动（仅保留原有 stock.low webhook 事件，回退 3.4 行为）</li>
 *   <li><b>notify</b> —— 站内信通知仓库管理员（R-WAREHOUSE，通知自动叠加 SSE 实时推送）</li>
 *   <li><b>ticket</b> —— 通知 + 以系统操作人（admin）身份自动创建补货工单，
 *       建议补货量 = 安全库存 − 当前库存</li>
 * </ul>
 *
 * <p><b>不在 BEFORE_COMMIT 里做重活：</b>建单要走 WorkorderService 的完整校验链，
 * 在业务事务内抛错会污染事务（v3.4 CC-05 双连接教训）；出入库回滚时 afterCommit
 * 不触发，联动自然取消。禁用 @Scheduled / Spring 托管调度 —— 自建守护线程
 * （理由见 SseConnectionRegistry）。
 *
 * <p><b>幂等（防重复建单），三层防线：</b>
 * <ol>
 *   <li>实例内存防抖：60s 内同物料只处理一次（挡住连续出库的高频触发；跨实例场景由下层兜底）</li>
 *   <li>material 行 FOR UPDATE：并发的低库存处理在同一物料行上自然串行化——锁住的正是
 *       要核对的数据行本身，同时避开 pg_advisory_xact_lock 的 void 返回值在
 *       JdbcTemplate 上的类型处理问题；拿锁后重查库存，已回升则跳过</li>
 *   <li>开放工单查重：存在未完结（待处理/待审批/处理中）的同类型补货单且关联物料 CSV
 *       精确命中本物料（排除逻辑删除行）则跳过</li>
 * </ol>
 */
@Service
public class LowStockService {
    private static final Logger log = LoggerFactory.getLogger(LowStockService.class);
    /** 实例内防抖窗口：同物料 60s 内只联动一次（通知是提醒性质，无需每次出库都提醒） */
    static final long DEDUP_MS = 60_000L;
    private static final String NOTIFY_TYPE = "STOCK_LOW";
    private static final String WAREHOUSE_ROLE = "R-WAREHOUSE";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final ActorContext actorContext;
    private final WorkorderService workorderService;
    private final NotificationPort notificationPort;
    private final String mode;
    private final String woType;
    private final Map<String, Long> lastSeenAt = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "low-stock-linkage");
        t.setDaemon(true);
        return t;
    });

    public LowStockService(
        JdbcTemplate jdbc,
        TransactionTemplate transactionTemplate,
        ActorContext actorContext,
        WorkorderService workorderService,
        NotificationPort notificationPort,
        @Value("${dcim.linkage.low-stock.mode:notify}") String mode,
        @Value("${dcim.linkage.low-stock.wo-type:补货}") String woType
    ) {
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.actorContext = actorContext;
        this.workorderService = workorderService;
        this.notificationPort = notificationPort;
        this.mode = mode == null ? "notify" : mode.trim();
        this.woType = woType == null || woType.isBlank() ? "补货" : woType.trim();
    }

    /**
     * checkLowStock 命中时回调（处于业务事务内）：仅注册 afterCommit 钩子，
     * 实际联动在事务提交后由守护线程执行；off 模式零开销直接返回。
     */
    public void onLowStock(String materialCode, String name, int stock, int safetyStock, String unit, String triggerLogNo) {
        if (this.isOff() || materialCode == null || materialCode.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = this.lastSeenAt.get(materialCode);
        if (last != null && now - last < DEDUP_MS) {
            return;
        }
        this.lastSeenAt.put(materialCode, now);
        String code = materialCode.trim();
        Runnable job = () -> this.handle(code, name, stock, safetyStock, unit, triggerLogNo);
        // 触发时的快照仅用于 notify 模式的文案；ticket 模式在事务内重查最新值
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    LowStockService.this.submit(job);
                }
            });
        } else {
            this.submit(job);
        }
    }

    private boolean isOff() {
        return "off".equalsIgnoreCase(this.mode);
    }

    private void submit(Runnable job) {
        try {
            this.executor.execute(() -> {
                try {
                    job.run();
                } catch (RuntimeException e) {
                    log.warn("[linkage] 低库存联动处理失败（不影响出入库）：{}", e.toString(), e);
                }
            });
        } catch (RuntimeException e) {
            log.warn("[linkage] 低库存联动任务提交失败：{}", e.toString());
        }
    }

    private void handle(String code, String name, int stock, int safetyStock, String unit, String triggerLogNo) {
        if ("ticket".equalsIgnoreCase(this.mode)) {
            this.handleTicket(code, name, unit, triggerLogNo);
        } else {
            this.notifyWarehouse(code, name, stock, safetyStock, unit);
        }
    }

    /** notify 模式：站内信通知仓库管理员（NotificationAdapter 落库后自动叠加 SSE 实时推送）。 */
    private void notifyWarehouse(String code, String name, int stock, int safetyStock, String unit) {
        String title = "物料库存低于安全线";
        String content = "物料 " + code + "「" + name + "」当前库存 " + stock + (unit == null ? "" : " " + unit)
            + "，已低于安全库存 " + safetyStock + "，请及时安排补货";
        this.notificationPort.notifyRole(WAREHOUSE_ROLE, NOTIFY_TYPE, title, content, code);
    }

    /** ticket 模式：新事务内行锁 → 重查 → 查重 → 建单；建单成功后通知仓库管理员。 */
    private void handleTicket(String code, String name, String unit, String triggerLogNo) {
        String woNo = this.transactionTemplate.execute(status -> this.createTicketIfAbsent(code, name, unit, triggerLogNo));
        if (woNo != null) {
            String title = "物料低库存已自动创建补货工单";
            String content = "物料 " + code + "「" + name + "」库存低于安全线，系统已自动创建补货工单 "
                + woNo + "，请及时跟进采购 / 入库（触发流水：" + (triggerLogNo == null ? "未知" : triggerLogNo) + "）";
            this.notificationPort.notifyRole(WAREHOUSE_ROLE, NOTIFY_TYPE, title, content, woNo);
        }
    }

    /**
     * 在调用方事务（executor 线程开启的新事务）内执行：material 行锁 → 重查库存 →
     * 开放补货单查重 → 建单。返回新建工单号；库存已回升 / 已有开放单 / 配置异常返回 null。
     */
    private String createTicketIfAbsent(String code, String name, String unit, String triggerLogNo) {
        Map<String, Object> m = this.jdbc
            .queryForMap("SELECT name, unit, COALESCE(stock, 0) AS stock, COALESCE(safety_stock, 0) AS safety FROM material WHERE material_code = ? FOR UPDATE", code);
        String materialName = m.get("name") == null ? name : String.valueOf(m.get("name"));
        String materialUnit = m.get("unit") == null ? unit : String.valueOf(m.get("unit"));
        int stock = ((Number) m.get("stock")).intValue();
        int safety = ((Number) m.get("safety")).intValue();
        if (safety <= 0 || stock >= safety) {
            log.info("[linkage] 物料 {} 低库存联动跳过：库存已回升或安全库存无效（stock={}, safety={}）", code, stock, safety);
            return null;
        }
        Integer open = this.jdbc
            .queryForObject(
                "SELECT COUNT(*) FROM workorder WHERE deleted_at IS NULL AND wo_type = ?"
                    + " AND status IN ('待处理','待审批','处理中')"
                    + " AND (',' || COALESCE(related_materials, '') || ',') LIKE '%,' || ? || ',%'",
                Integer.class,
                this.woType,
                code
            );
        if (open != null && open > 0) {
            log.info("[linkage] 物料 {} 已存在未完结的「{}」工单，跳过重复建单", code, this.woType);
            return null;
        }
        int suggest = safety - stock;
        String title = "补货：" + materialName + "（" + code + "）";
        String desc = "物料 " + code + "「" + materialName + "」当前库存 " + stock + (materialUnit == null ? "" : " " + materialUnit)
            + "，低于安全库存 " + safety + "，建议补货 " + suggest + "。本工单由系统在库存低于安全线时自动创建（触发流水："
            + (triggerLogNo == null ? "未知" : triggerLogNo) + "），请仓库管理员跟进采购 / 入库。";
        WorkorderEntity wo = this.actorContext
            .runAs(
                null,
                () -> this.workorderService
                    .create(this.woType, title, desc, "高", null, List.of(code), suggest, null, null, null, null, null, null, null, null, null)
            );
        log.info("[linkage] 物料 {} 低库存自动创建「{}」工单 {}（stock={}, safety={}, 建议量={}）", code, this.woType, wo.getWoNo(), stock, safety, suggest);
        return wo.getWoNo();
    }

    @PreDestroy
    void stop() {
        this.executor.shutdownNow();
    }
}
