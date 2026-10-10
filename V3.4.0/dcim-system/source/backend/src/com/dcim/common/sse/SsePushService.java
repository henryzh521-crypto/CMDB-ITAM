package com.dcim.common.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * SSE 推送门面（v3.5）：本机直发 + Redis pub/sub 跨实例转发。
 *
 * <p>双实例部署时用户连在任一实例上：推送先发本机全部目标连接，再把信封
 * （发送实例标识 + 目标用户 + 载荷）发布到 {@value #CHANNEL}，其他实例的
 * {@link RedisSseRelay} 订阅后在本机分发；自己的回声按实例标识跳过，
 * 保证每条消息在每实例恰好分发一次。
 *
 * <p>未配置 Redis（REDIS_HOST 空 → 无 StringRedisTemplate）时自动降级为单机模式：
 * 仅本机连接收到实时推送，其余由前端 30s 轮询兜底，功能不中断。
 *
 * <p>所有方法均为尽力而为：任何失败只记 WARN，不向业务线程抛异常。
 */
@Service
public class SsePushService {
    /** Redis pub/sub 频道：SSE 跨实例转发信封 */
    public static final String CHANNEL = "dcim:sse";
    private static final Logger log = LoggerFactory.getLogger(SsePushService.class);
    private final SseConnectionRegistry registry;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    /** 本实例标识（跨实例信封去回声用） */
    private final String instanceId;

    public SsePushService(
        SseConnectionRegistry registry,
        ObjectProvider<StringRedisTemplate> redisProvider,
        ObjectMapper objectMapper,
        @Value("${dcim.sse.enabled:true}") boolean enabled
    ) {
        this.registry = registry;
        this.redisProvider = redisProvider;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.instanceId = UUID.randomUUID().toString().substring(0, 8);
    }

    /** 推送给指定用户的全部在线连接（本机直发 + 其他实例转发）。返回本机送达数。 */
    public int pushUser(String username, Object payload) {
        if (!this.enabled || username == null || username.isBlank()) {
            return 0;
        }
        int sent = this.registry.sendToUser(username, payload);
        this.relay(username, payload);
        return sent;
    }

    /** 广播给全部在线连接（本机直发 + 其他实例转发）。返回本机送达数。 */
    public int broadcast(Object payload) {
        if (!this.enabled) {
            return 0;
        }
        int sent = this.registry.broadcast(payload);
        this.relay(null, payload);
        return sent;
    }

    /** RedisSseRelay 收到其他实例的信封后回灌本机分发。 */
    public void onRelayed(Map<String, Object> envelope) {
        if (!this.enabled || this.instanceId.equals(envelope.get("inst"))) {
            return;
        }
        Object payload = envelope.get("payload");
        Object user = envelope.get("user");
        if (user == null) {
            this.registry.broadcast(payload);
        } else {
            this.registry.sendToUser(String.valueOf(user), payload);
        }
    }

    private void relay(String username, Object payload) {
        StringRedisTemplate redis = this.redisProvider.getIfAvailable();
        if (redis == null) {
            return; // 无 Redis：单机模式，仅本机分发
        }
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("inst", this.instanceId);
            envelope.put("user", username);
            envelope.put("payload", payload);
            redis.convertAndSend(CHANNEL, this.objectMapper.writeValueAsString(envelope));
        } catch (RuntimeException | JsonProcessingException e) {
            // Redis 抖动只影响跨实例推送，本机已直发；不抛给业务线程
            log.warn("[sse] Redis 转发失败（本机已送达）：{}", e.toString());
        }
    }
}
