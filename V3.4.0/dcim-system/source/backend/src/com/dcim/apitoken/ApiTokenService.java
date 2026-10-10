package com.dcim.apitoken;

import com.dcim.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ApiTokenService {

    private static final Logger log = LoggerFactory.getLogger(ApiTokenService.class);
    public static final String PREFIX = "dcim_pat_";
    /** v3.5.2：吊销跨实例广播频道（无 Redis 的单实例部署无需广播，本地缓存已同步清理） */
    public static final String INVALIDATION_CHANNEL = "dcim:pat-invalidate";
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final long CACHE_MS = 60000L;
    private static final int MAX_PER_USER = 20;
    private final JdbcTemplate jdbc;
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redisProvider;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, ApiTokenService.Cached> cache = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastTouch = new ConcurrentHashMap<>();

    public ApiTokenService(
        JdbcTemplate jdbc,
        org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redisProvider
    ) {
        this.jdbc = jdbc;
        this.redisProvider = redisProvider;
    }

    public static boolean looksLikePat(String token) {
        return token != null && token.startsWith("dcim_pat_") && token.length() == "dcim_pat_".length() + 40;
    }

    public ApiTokenService.Principal verify(String token, String ip) {
        if (!looksLikePat(token)) {
            return null;
        } else {
            String hash = sha256(token);
            ApiTokenService.Cached c = this.cache.get(hash);
            long now = System.currentTimeMillis();
            ApiTokenService.Principal p;
            if (c != null && now - c.at() < 60000L) {
                p = c.p();
            } else {
                List<Map<String, Object>> rows = this.jdbc
                    .queryForList(
                        "SELECT t.id, t.username, t.read_only FROM api_token t\nJOIN users u ON u.username = t.username AND u.deleted_at IS NULL AND u.status = 1\nWHERE t.token_hash = ? AND t.revoked_at IS NULL AND (t.expires_at IS NULL OR t.expires_at > NOW())\n",
                        new Object[]{hash}
                    );
                p = rows.isEmpty()
                    ? null
                    : new ApiTokenService.Principal(
                        ((Number)rows.get(0).get("id")).longValue(), (String)rows.get(0).get("username"), Boolean.TRUE.equals(rows.get(0).get("read_only"))
                    );
                if (this.cache.size() > 10000) {
                    this.cache.clear();
                }

                this.cache.put(hash, new ApiTokenService.Cached(p, now));
            }

            if (p != null) {
                this.touch(p.id(), ip);
            }

            return p;
        }
    }

    private void touch(long id, String ip) {
        long now = System.currentTimeMillis();
        Long last = this.lastTouch.get(id);
        if (last == null || now - last >= 300000L) {
            this.lastTouch.put(id, now);

            try {
                this.jdbc
                    .update(
                        "UPDATE api_token SET last_used_at = NOW(), last_used_ip = ? WHERE id = ?",
                        new Object[]{ip == null ? null : ip.substring(0, Math.min(64, ip.length())), id}
                    );
            } catch (RuntimeException var8) {
                log.debug("[pat] 更新最近使用失败：{}", var8.getMessage());
            }
        }
    }

    public Map<String, Object> create(String owner, String name, boolean readOnly, Integer expireDays, String createdBy) {
        if (name != null && !name.isBlank() && name.length() <= 100) {
            if (expireDays == null || expireDays >= 1 && expireDays <= 3650) {
                Long n = (Long)this.jdbc
                    .queryForObject("SELECT COUNT(*) FROM api_token WHERE username = ? AND revoked_at IS NULL", Long.class, new Object[]{owner});
                if (n != null && n >= 20L) {
                    throw BusinessException.conflict("每个用户最多 20 个有效令牌，请先吊销不用的令牌");
                } else {
                    StringBuilder sb = new StringBuilder("dcim_pat_");

                    for (int i = 0; i < 40; i++) {
                        sb.append(ALPHABET[this.random.nextInt(ALPHABET.length)]);
                    }

                    String token = sb.toString();
                    LocalDateTime exp = expireDays == null ? null : LocalDateTime.now().plusDays((long)expireDays.intValue());
                    Long id = (Long)this.jdbc
                        .queryForObject(
                            "INSERT INTO api_token (name, token_hash, token_prefix, username, read_only, expires_at, created_by)\nVALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id\n",
                            Long.class,
                            new Object[]{name.trim(), sha256(token), token.substring(0, "dcim_pat_".length() + 6), owner, readOnly, exp, createdBy}
                        );
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("id", id);
                    out.put("token", token);
                    out.put("expiresAt", exp);
                    return out;
                }
            } else {
                throw BusinessException.badRequest("有效期须在 1-3650 天（不填 = 永久，不推荐）");
            }
        } else {
            throw BusinessException.badRequest("请填写令牌用途（不超过 100 字）");
        }
    }

    public List<Map<String, Object>> list(String owner) {
        String sql = "SELECT id, name, token_prefix AS \"tokenPrefix\", username, read_only AS \"readOnly\", expires_at AS \"expiresAt\",\n       last_used_at AS \"lastUsedAt\", last_used_ip AS \"lastUsedIp\", created_at AS \"createdAt\",\n       revoked_at AS \"revokedAt\",\n       CASE WHEN revoked_at IS NOT NULL THEN 'REVOKED'\n            WHEN expires_at IS NOT NULL AND expires_at <= NOW() THEN 'EXPIRED' ELSE 'ACTIVE' END AS state\nFROM api_token";
        return owner == null
            ? this.jdbc.queryForList(sql + " ORDER BY revoked_at NULLS FIRST, id DESC LIMIT 500")
            : this.jdbc.queryForList(sql + " WHERE username = ? ORDER BY revoked_at NULLS FIRST, id DESC", new Object[]{owner});
    }

    public void revoke(long id, String owner) {
        int n = owner == null
            ? this.jdbc.update("UPDATE api_token SET revoked_at = NOW() WHERE id = ? AND revoked_at IS NULL", new Object[]{id})
            : this.jdbc.update("UPDATE api_token SET revoked_at = NOW() WHERE id = ? AND username = ? AND revoked_at IS NULL", new Object[]{id, owner});
        if (n == 0) {
            throw BusinessException.notFound("令牌不存在或已吊销");
        } else {
            this.evictAllCache();
            this.broadcastInvalidation();
        }
    }

    public void revokeAllOf(String username) {
        this.jdbc.update("UPDATE api_token SET revoked_at = NOW() WHERE username = ? AND revoked_at IS NULL", new Object[]{username});
        this.evictAllCache();
        this.broadcastInvalidation();
    }

    /** v3.5.2：清空本实例校验缓存（吊销后立即生效；亦供 Redis 订阅方回调） */
    public void evictAllCache() {
        this.cache.clear();
    }

    /**
     * v3.5.2：吊销跨实例广播。校验缓存 60s TTL，实例 A 吊销后实例 B 缓存中的旧结果
     * 此前要等自然过期才拒绝（多实例下的吊销窗口）；发布到 Redis 频道后由
     * {@link RedisPatInvalidator} 订阅并立即清缓存。尽力而为，失败不影响吊销结果
     * （各实例最迟 60s 后自然失效）。
     */
    private void broadcastInvalidation() {
        try {
            org.springframework.data.redis.core.StringRedisTemplate redis = this.redisProvider.getIfAvailable();
            if (redis != null) {
                redis.convertAndSend(INVALIDATION_CHANNEL, String.valueOf(System.currentTimeMillis()));
            }
        } catch (RuntimeException e) {
            log.debug("[pat] 吊销广播失败（各实例缓存最迟 60s 后自然失效）：{}", e.getMessage());
        }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException var2) {
            throw new IllegalStateException(var2);
        }
    }

    private static record Cached(ApiTokenService.Principal p, long at) {
    }

    public static record Principal(long id, String username, boolean readOnly) {
    }
}
