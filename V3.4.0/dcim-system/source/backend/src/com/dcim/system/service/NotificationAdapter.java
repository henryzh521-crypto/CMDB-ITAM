package com.dcim.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.notify.NotificationPort;
import com.dcim.common.sse.SsePushService;
import com.dcim.system.entity.NotificationEntity;
import com.dcim.system.entity.UserEntity;
import com.dcim.system.entity.UserRoleEntity;
import com.dcim.system.mapper.NotificationMapper;
import com.dcim.system.mapper.UserMapper;
import com.dcim.system.mapper.UserRoleMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class NotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationAdapter.class);
    private final NotificationMapper notificationMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserMapper userMapper;
    private final SsePushService ssePush;
    /** 站内信落库专用 NESTED 保存点模板（同 WebhookDispatcher 惯例），见 {@link #insert} */
    private final TransactionTemplate nestedTx;

    @Transactional
    @Override
    public void notifyUser(String username, String type, String title, String content, String refId) {
        if (username != null && !username.isBlank()) {
            this.insert(username.trim(), type, title, content, refId);
        }
    }

    @Transactional
    @Override
    public void notifyRole(String roleCode, String type, String title, String content, String refId) {
        if (roleCode != null && !roleCode.isBlank()) {
            List<Long> userIds = this.userRoleMapper
                .selectList(Wrappers.<UserRoleEntity>lambdaQuery().eq(UserRoleEntity::getRoleCode, roleCode.trim()))
                .stream()
                .map(UserRoleEntity::getUserId)
                .toList();
            if (!userIds.isEmpty()) {
                for (String username : this.userMapper
                    .selectList((Wrappers.<UserEntity>lambdaQuery().in(UserEntity::getId, userIds)).eq(UserEntity::getStatus, 1))
                    .stream()
                    .map(UserEntity::getUsername)
                    .toList()) {
                    this.insert(username, type, title, content, refId);
                }
            }
        }
    }

    /**
     * 单条站内信落库 + 实时推送。落库在 NESTED 保存点中执行（D3 修复）：
     * insert 失败只回滚到保存点并被就地吞掉，建单/出入库等外层业务事务不再被
     * 站内信拖垮回滚（与 WebhookDispatcher 的保存点隔离同语义；无外层事务时
     * NESTED 退化为独立新事务，同样安全）。推送只在落库成功后进行。
     */
    private void insert(String username, String type, String title, String content, String refId) {
        NotificationEntity n = new NotificationEntity();
        n.setUsername(username);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefId(refId);
        n.setReadFlag(false);
        n.setCreatedAt(LocalDateTime.now());
        try {
            this.nestedTx.executeWithoutResult(status -> this.notificationMapper.insert(n));
        } catch (RuntimeException e) {
            log.error("站内信落库失败，业务不受影响：username={}, type={}, refId={}", username, type, refId, e);
            return;
        }
        this.pushRealtime(this.toPayload(n));
    }

    /**
     * 站内信落库后叠加 SSE 实时推送（v3.5）。有事务时注册到提交后回调 ——
     * 业务回滚的通知不会推送出去；无事务上下文则立即推送。
     * 推送失败只记 WARN，绝不影响站内信与业务主流程。
     */
    private void pushRealtime(Map<String, Object> payload) {
        Runnable push = () -> {
            try {
                this.ssePush.pushUser((String) payload.get("username"), payload);
            } catch (RuntimeException e) {
                log.warn("[sse] 通知实时推送失败（不影响业务）：{}", e.toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }
    }

    /** SSE 帧载荷：与 NotificationController /my 接口的行结构保持一致，前端可直接合并。 */
    private Map<String, Object> toPayload(NotificationEntity n) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", "notification");
        row.put("id", n.getId());
        row.put("username", n.getUsername());
        row.put("type", n.getType());
        row.put("title", n.getTitle());
        row.put("content", n.getContent());
        row.put("refId", n.getRefId());
        row.put("readFlag", n.getReadFlag());
        row.put("createdAt", n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
        return row;
    }

    public NotificationAdapter(final NotificationMapper notificationMapper, final UserRoleMapper userRoleMapper, final UserMapper userMapper, final SsePushService ssePush, final PlatformTransactionManager txm) {
        this.notificationMapper = notificationMapper;
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
        this.ssePush = ssePush;
        this.nestedTx = new TransactionTemplate(txm);
        this.nestedTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }
}
