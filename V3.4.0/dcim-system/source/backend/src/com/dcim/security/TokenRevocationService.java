package com.dcim.security;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 会话吊销（v3.3）。
 *
 * <p>v3.2 的 access token 是纯无状态 JWT：登出 / 改密 / 管理员重置 / 停用后，
 * 已签发的 access token 在 2 小时有效期内仍可继续调用全部接口。现在分两种粒度：
 * <ul>
 *   <li>{@link #revokeAll}：推进 users.token_valid_after 水位线，此前签发的全部会话失效（改密/重置/停用/删除）；</li>
 *   <li>{@link #revokeCurrent}：只吊销当前会话（登出）——把当前 JWT 的 jti 记入短期黑名单，并删除当前 refresh token。</li>
 * </ul>
 */
@Service
public class TokenRevocationService {
    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);
    private static final String JTI_PREFIX = "dcim:auth:revoked-jti:";
    private final JdbcTemplate jdbc;
    private final TokenStore tokenStore;
    private final LoginUserLoader loginUserLoader;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    /** 无 Redis 时的单机兜底：jti → 过期时刻（毫秒） */
    private final Map<String, Long> localRevoked = new ConcurrentHashMap<>();

    public TokenRevocationService(
        JdbcTemplate jdbc, TokenStore tokenStore, LoginUserLoader loginUserLoader, ObjectProvider<StringRedisTemplate> redisProvider
    ) {
        this.jdbc = jdbc;
        this.tokenStore = tokenStore;
        this.loginUserLoader = loginUserLoader;
        this.redisProvider = redisProvider;
    }

    /** 吊销该用户此刻之前签发的全部会话（access + refresh），用于改密 / 重置密码 / 停用 / 删除。 */
    public void revokeAll(String username, String reason) {
        if (username == null || username.isBlank()) {
            return;
        }
        try {
            // clock_timestamp()：语句执行的真实时刻（now() 是事务开始时刻，长事务内会偏早）
            this.jdbc.update("UPDATE users SET token_valid_after = clock_timestamp() WHERE username = ?", username);
        } catch (RuntimeException e) {
            log.warn("[auth] 推进令牌水位线失败 user={}：{}", username, e.getMessage());
        }
        try {
            this.tokenStore.removeAll(username);
        } catch (RuntimeException e) {
            log.warn("[auth] 删除 refresh token 失败 user={}：{}", username, e.getMessage());
        }
        this.loginUserLoader.evict(username);
        log.info("[auth] 已吊销用户 {} 的全部会话（{}）", username, reason);
    }

    /** 只吊销当前会话（登出）：其它终端不受影响。 */
    public void revokeCurrent(String jti, long expiresAtMillis, String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                this.tokenStore.remove(refreshToken);
            } catch (RuntimeException e) {
                log.warn("[auth] 删除当前 refresh token 失败：{}", e.getMessage());
            }
        }
        if (jti == null || jti.isBlank()) {
            return;
        }
        long ttlMs = expiresAtMillis - System.currentTimeMillis();
        if (ttlMs <= 0) {
            return;
        }
        StringRedisTemplate redis = this.redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                redis.opsForValue().set(JTI_PREFIX + jti, "1", Duration.ofMillis(ttlMs + 1000L));
                return;
            } catch (RuntimeException e) {
                log.warn("[auth] 写入 jti 黑名单失败，退化为本机内存：{}", e.getMessage());
            }
        }
        this.pruneLocal();
        this.localRevoked.put(jti, expiresAtMillis);
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Long local = this.localRevoked.get(jti);
        if (local != null && local > System.currentTimeMillis()) {
            return true;
        }
        StringRedisTemplate redis = this.redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                return Boolean.TRUE.equals(redis.hasKey(JTI_PREFIX + jti));
            } catch (RuntimeException e) {
                log.debug("[auth] 查询 jti 黑名单失败：{}", e.getMessage());
            }
        }
        return false;
    }

    private void pruneLocal() {
        if (this.localRevoked.size() < 1000) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = this.localRevoked.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() <= now) {
                it.remove();
            }
        }
    }
}
