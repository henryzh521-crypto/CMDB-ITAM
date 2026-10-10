package com.dcim.common.sse;

import com.dcim.security.RedisEnabledCondition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis pub/sub → SSE 跨实例转发（v3.5）。
 *
 * <p>仅当配置了 Redis（REDIS_HOST 非空，见 {@link RedisEnabledCondition}）时装配；
 * 订阅 {@link SsePushService#CHANNEL}，把其他实例发布的信封交给
 * {@link SsePushService#onRelayed} 在本机分发。
 *
 * <p>SSE 停用（dcim.sse.enabled=false）时不订阅任何频道 —— 连接本身也建不起来，
 * 完整回到 3.4 行为（整体回退开关的一部分）。
 */
@Configuration(proxyBeanMethods = false)
@Conditional(RedisEnabledCondition.class)
public class RedisSseRelay implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(RedisSseRelay.class);
    private final SsePushService pushService;
    private final ObjectMapper objectMapper;
    private final boolean sseEnabled;

    public RedisSseRelay(SsePushService pushService, ObjectMapper objectMapper, @Value("${dcim.sse.enabled:true}") boolean sseEnabled) {
        this.pushService = pushService;
        this.objectMapper = objectMapper;
        this.sseEnabled = sseEnabled;
    }

    @Bean
    public RedisMessageListenerContainer dcimSseRelayContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        if (this.sseEnabled) {
            container.addMessageListener(this, new ChannelTopic(SsePushService.CHANNEL));
            log.info("[sse] 已订阅 Redis 频道 {}（跨实例转发）", SsePushService.CHANNEL);
        }
        return container;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Map<String, Object> envelope = this.objectMapper.readValue(message.getBody(), new TypeReference<Map<String, Object>>() {});
            this.pushService.onRelayed(envelope);
        } catch (RuntimeException | IOException e) {
            log.warn("[sse] 跨实例信封解析失败：{}", e.toString());
        }
    }
}
