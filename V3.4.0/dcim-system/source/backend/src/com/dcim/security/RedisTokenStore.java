package com.dcim.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 版 refresh token 存储。
 *
 * <p>v3.3：
 * <ul>
 *   <li>键名存 token 的 SHA-256 摘要（兼容读取 v3.2 遗留的明文键，自然过期后消失）；</li>
 *   <li>{@link #consume} 用 GETDEL 原子消费，杜绝并发重放换发多份令牌；</li>
 *   <li>按用户批量吊销改用 SCAN，避免 KEYS 阻塞 Redis。</li>
 * </ul>
 */
public class RedisTokenStore implements TokenStore {
    private static final String TOKEN_PREFIX = "dcim:auth:refresh:";
    private static final String USER_PREFIX = "dcim:auth:refresh-user:";
    private static final String ROTATED_PREFIX = "dcim:auth:refresh-rotated:";
    private static final Duration ROTATE_GRACE = Duration.ofSeconds(30L);
    private final StringRedisTemplate redis;

    public RedisTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String refreshToken, String username, Duration ttl) {
        String h = TokenStore.digest(refreshToken);
        this.redis.opsForValue().set(TOKEN_PREFIX + h, username, ttl);
        this.redis.opsForValue().set(USER_PREFIX + username + ":" + h, "1", ttl);
    }

    @Override
    public Optional<String> findUsername(String refreshToken) {
        String v = this.redis.opsForValue().get(TOKEN_PREFIX + TokenStore.digest(refreshToken));
        if (v == null) {
            v = this.redis.opsForValue().get(TOKEN_PREFIX + refreshToken);
        }
        return Optional.ofNullable(v);
    }

    @Override
    public Optional<String> consume(String refreshToken) {
        String h = TokenStore.digest(refreshToken);
        String username = this.redis.opsForValue().getAndDelete(TOKEN_PREFIX + h);
        String indexKeyPart = h;
        if (username == null) {
            // v3.2 遗留明文键
            username = this.redis.opsForValue().getAndDelete(TOKEN_PREFIX + refreshToken);
            indexKeyPart = refreshToken;
        }
        if (username != null) {
            this.redis.delete(USER_PREFIX + username + ":" + indexKeyPart);
            this.redis.opsForValue().set(ROTATED_PREFIX + h, username, ROTATE_GRACE);
        }
        return Optional.ofNullable(username);
    }

    @Override
    public boolean recentlyRotated(String refreshToken) {
        return Boolean.TRUE.equals(this.redis.hasKey(ROTATED_PREFIX + TokenStore.digest(refreshToken)));
    }

    @Override
    public void remove(String refreshToken) {
        this.consume(refreshToken);
    }

    @Override
    public void removeAll(String username) {
        // 吊销全部会话时，宽限标记也一并失效（否则改密后 30 秒内旧 refresh 仍会被当作“并发续期”放行）
        try (Cursor<String> cur = this.redis.scan(ScanOptions.scanOptions().match(ROTATED_PREFIX + "*").count(500L).build())) {
            List<String> rk = new ArrayList<>();
            while (cur.hasNext()) {
                String k = cur.next();
                if (username.equals(this.redis.opsForValue().get(k))) {
                    rk.add(k);
                }
            }
            if (!rk.isEmpty()) {
                this.redis.delete(rk);
            }
        }
        String prefix = USER_PREFIX + username + ":";
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cur = this.redis.scan(ScanOptions.scanOptions().match(prefix + "*").count(500L).build())) {
            while (cur.hasNext()) {
                keys.add(cur.next());
            }
        }
        List<String> del = new ArrayList<>(keys.size() * 2);
        for (String key : keys) {
            del.add(TOKEN_PREFIX + key.substring(prefix.length()));
            del.add(key);
        }
        if (!del.isEmpty()) {
            this.redis.delete(del);
        }
    }
}
