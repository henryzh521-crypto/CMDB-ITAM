package com.dcim.webhook.service;

import com.dcim.common.event.DomainEvent;
import com.dcim.warehouse.service.LowStockService;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 领域事件 → Webhook 投递记录（v3.4 改为「事务性发件箱」）。
 *
 * <p>v3.3 及以前在 AFTER_COMMIT 阶段以 REQUIRES_NEW 另开事务写投递记录。AFTER_COMMIT 回调执行时
 * 原事务的连接尚未归还，REQUIRES_NEW 会再借一条：每个写请求同时占用两条连接。并发写请求数接近
 * 连接池大小时，所有请求都握着第一条、等第二条，30 秒后成批报
 * {@code CannotCreateTransactionException}（v3.4 并发用例 CC-05：20 路并发建单出现 500）。
 *
 * <p>现在在 BEFORE_COMMIT 阶段、于同一事务内用保存点（NESTED）写投递记录：
 * <ul>
 *   <li>只用请求已持有的那一条连接，不再有「持一等一」的连接池饥饿；</li>
 *   <li>业务数据与投递记录同生共死：业务回滚则不投递，进程在提交后崩溃也不丢事件；</li>
 *   <li>写投递记录失败只回滚到保存点并记录告警，不影响业务提交（与旧版「尽力而为」语义一致）。</li>
 * </ul>
 * 事务外发布的事件（fallbackExecution）仍立即执行，此时 NESTED 等同于新开事务。
 *
 * <p>没有任何启用的端点订阅该事件时直接跳过（订阅关系缓存 {@value #SUBSCRIPTION_TTL_MS} 毫秒），
 * 批量导入等一次产生上万事件的事务不会因此多出上万个保存点。新建 / 启用的 Webhook 最迟在缓存过期后生效。
 */
@Component
public class WebhookDispatcher {
    
    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    private final WebhookService webhookService;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate savepoint;
    private final LowStockService lowStockService;
    static final long SUBSCRIPTION_TTL_MS = 5000L;
    private volatile Set<String> subscribed;
    private volatile long subscribedAt;

    public WebhookDispatcher(WebhookService webhookService, JdbcTemplate jdbc, PlatformTransactionManager txm, LowStockService lowStockService) {
        this.webhookService = webhookService;
        this.jdbc = jdbc;
        this.savepoint = new TransactionTemplate(txm);
        this.savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        this.lowStockService = lowStockService;
    }

    @TransactionalEventListener(
        phase = TransactionPhase.BEFORE_COMMIT,
        fallbackExecution = true
    )
    public void onEvent(DomainEvent event) {
        try {
            // v3.5.1：低库存联动（站内信 / 自动补货单）是平台内部能力，只由 dcim.linkage.low-stock.mode
            // 决定，不能被「无人订阅 webhook」门控——否则没配 Webhook 的部署里联动整体失效（CT-13 回归）。
            // stock.movement 因此总是进入保存点：无订阅时 enqueue 对不匹配事件自然返回 0（不写投递行），
            // 额外开销只是一条端点 SELECT；出入库并非批量导入热点，其余事件类型的跳过优化保持不变。
            if (!"stock.movement".equals(event.type()) && !this.anySubscriber(event.type())) {
                return;
            }
            this.savepoint.executeWithoutResult(status -> {
                int n = this.webhookService.enqueue(event);
                if (n > 0) {
                    log.debug("[webhook] {} {} 入队 {} 条", new Object[]{event.type(), event.objId(), n});
                }

                if ("stock.movement".equals(event.type())) {
                    this.checkLowStock(event);
                }
            });
        } catch (RuntimeException var3) {
            log.warn("[webhook] 事件 {} 入队失败（已回滚到保存点，业务照常提交）：{}", event.type(), var3.toString());
        }
    }

    /** 是否有启用的端点订阅该事件（含 "*"；库存流水还要看 stock.low，因为低库存告警由它派生）。 */
    boolean anySubscriber(String type) {
        Set<String> s = this.subscribed;
        long now = System.currentTimeMillis();
        if (s == null || now - this.subscribedAt > SUBSCRIPTION_TTL_MS) {
            try {
                // 放在保存点里：即使查询出错也只回滚到保存点，不会让业务事务进入 aborted 状态
                s = this.savepoint.execute(st -> new HashSet<>(this.jdbc.queryForList(
                    "SELECT DISTINCT unnest(event_types) FROM webhook_endpoint WHERE enabled = true AND deleted_at IS NULL", String.class)));
            } catch (RuntimeException e) {
                log.warn("[webhook] 读取订阅关系失败，按有订阅处理：{}", e.toString());
                return true;
            }
            this.subscribed = s;
            this.subscribedAt = now;
        }
        return s.contains("*") || s.contains(type) || "stock.movement".equals(type) && s.contains("stock.low");
    }

    /** 端点增删改后立即让订阅缓存失效（本实例；其他实例在 TTL 内生效）。 */
    public void invalidateSubscriptions() {
        this.subscribed = null;
    }

    private void checkLowStock(DomainEvent event) {
        String logType = String.valueOf(event.data().get("logType"));
        if ("OUT".equals(logType) || "ADJUST".equals(logType) || "TRANSFER".equals(logType)) {
            List<Map<String, Object>> rows = this.jdbc
                .queryForList(
                    "SELECT material_code, name, COALESCE(stock, 0) AS stock, COALESCE(safety_stock, 0) AS safety_stock, unit FROM material WHERE material_code = ?",
                    new Object[]{event.objId()}
                );
            if (!rows.isEmpty()) {
                Map<String, Object> m = rows.get(0);
                int stock = ((Number)m.get("stock")).intValue();
                int safety = ((Number)m.get("safety_stock")).intValue();
                if (safety > 0 && stock < safety) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("materialCode", m.get("material_code"));
                    data.put("name", m.get("name"));
                    data.put("stock", stock);
                    data.put("safetyStock", safety);
                    data.put("unit", m.get("unit"));
                    data.put("trigger", event.data().get("logNo"));
                    this.webhookService.enqueue(DomainEvent.of("stock.low", "material", event.objId(), event.actor(), data));
                    // v3.5 联动：站内信通知 / 自动补货单。此处处于业务事务 BEFORE_COMMIT，
                    // LowStockService 只注册 afterCommit 钩子，实际联动在提交后异步执行
                    // （尽力而为，绝不影响出入库与 webhook 投递）
                    this.lowStockService
                        .onLowStock(
                            String.valueOf(m.get("material_code")),
                            String.valueOf(m.get("name")),
                            stock,
                            safety,
                            String.valueOf(m.get("unit")),
                            String.valueOf(event.data().get("logNo"))
                        );
                }
            }
        }
    }
}
