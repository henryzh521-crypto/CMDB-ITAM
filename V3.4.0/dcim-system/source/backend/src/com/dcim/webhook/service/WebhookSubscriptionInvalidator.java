package com.dcim.webhook.service;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Webhook 端点增删改后，让本实例的订阅关系缓存立即失效（v3.4）。
 * 其他实例在 {@link WebhookDispatcher#SUBSCRIPTION_TTL_MS} 内自然刷新。
 */
@Aspect
@Component
public class WebhookSubscriptionInvalidator {

    private final WebhookDispatcher dispatcher;

    public WebhookSubscriptionInvalidator(WebhookDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @AfterReturning("target(com.dcim.webhook.mapper.WebhookEndpointMapper) && (execution(* insert*(..)) || execution(* update*(..)) || execution(* delete*(..)))")
    public void endpointChanged() {
        this.dispatcher.invalidateSubscriptions();
    }
}
