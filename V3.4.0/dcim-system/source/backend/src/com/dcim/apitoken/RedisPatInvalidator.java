package com.dcim.apitoken;

import com.dcim.security.RedisEnabledCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * PAT 吊销跨实例即时失效（v3.5.2）。
 *
 * <p>{@link ApiTokenService} 的校验结果缓存 60s：实例 A 上吊销令牌后，实例 B 缓存中的
 * 旧结果仍会放行（最长 60s 吊销窗口）。本组件订阅 {@link ApiTokenService#INVALIDATION_CHANNEL}，
 * 任一实例吊销即广播，全部实例立即清空缓存。仅当配置了 Redis（多实例部署的前提）时装配；
 * 单实例无 Redis 时本地缓存已在吊销路径同步清理，无需广播。
 */
@Configuration(proxyBeanMethods = false)
@Conditional(RedisEnabledCondition.class)
public class RedisPatInvalidator implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(RedisPatInvalidator.class);
    private final ApiTokenService apiTokenService;

    public RedisPatInvalidator(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    @Bean
    public RedisMessageListenerContainer dcimPatInvalidatorContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(this, new ChannelTopic(ApiTokenService.INVALIDATION_CHANNEL));
        log.info("[pat] 已订阅 Redis 频道 {}（令牌吊销跨实例即时失效）", ApiTokenService.INVALIDATION_CHANNEL);
        return container;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            this.apiTokenService.evictAllCache();
        } catch (RuntimeException e) {
            log.warn("[pat] 吊销广播处理失败：{}", e.toString());
        }
    }
}
