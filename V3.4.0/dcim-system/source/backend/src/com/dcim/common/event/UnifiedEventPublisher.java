package com.dcim.common.event;

import com.dcim.common.notify.NotificationPort;
import org.springframework.stereotype.Service;

/**
 * 统一事件 / 通知门面（v3.5）。
 *
 * <p>组合既有 {@link DomainEventPublisher}（Spring 事件 → 事务性发件箱 / Webhook
 * 投递，语义不变）与 {@link NotificationPort}（站内信，NotificationAdapter 落库后
 * 自动叠加 SSE 按用户实时推送）。联动链路一处调用即完成
 * 「领域事件 + 站内信 + 实时推送」三路分发；既有 DomainEventPublisher 调用点不动。
 */
@Service
public class UnifiedEventPublisher {
    private final DomainEventPublisher events;
    private final NotificationPort notifications;

    public UnifiedEventPublisher(DomainEventPublisher events, NotificationPort notifications) {
        this.events = events;
        this.notifications = notifications;
    }

    /** 只发领域事件（发件箱 / Webhook / SSE 频道广播）。 */
    public void publish(DomainEvent event) {
        this.events.publish(event);
    }

    /** 只发站内信（落库 + SSE 按用户实时推送）。 */
    public void notifyUser(String username, String type, String title, String content, String refId) {
        this.notifications.notifyUser(username, type, title, content, refId);
    }

    /** 只发给指定角色的站内信（落库 + SSE 按用户实时推送）。 */
    public void notifyRole(String roleCode, String type, String title, String content, String refId) {
        this.notifications.notifyRole(roleCode, type, title, content, refId);
    }

    /** 领域事件 + 给指定用户的站内信（含 SSE 实时推送）。 */
    public void publishAndNotifyUser(DomainEvent event, String username, String type, String title, String content, String refId) {
        this.events.publish(event);
        this.notifications.notifyUser(username, type, title, content, refId);
    }

    /** 领域事件 + 给指定角色的站内信（含 SSE 实时推送）。 */
    public void publishAndNotifyRole(DomainEvent event, String roleCode, String type, String title, String content, String refId) {
        this.events.publish(event);
        this.notifications.notifyRole(roleCode, type, title, content, refId);
    }
}
