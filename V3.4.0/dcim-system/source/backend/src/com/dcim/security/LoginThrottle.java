package com.dcim.security;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 登录失败计数（防爆破）。
 *
 * <p>v3.3：配置了 Redis 时计数放在 Redis（多实例共享、重启不清零）；Redis 不可用时自动退回本机内存，
 * 行为与 v3.2 一致。v3.2 计数只在单个 JVM 内存里，后端扩容到多实例后攻击者每台各有一份额度。
 */
@Component
public class LoginThrottle {
    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);
    private static final int MAX_TRACKED = 10000;
    private static final String PREFIX = "dcim:login-throttle:";
    private static final RedisScript<Long> INCR = new DefaultRedisScript<>(
        "local c = redis.call('INCR', KEYS[1]) if c == 1 or redis.call('PTTL', KEYS[1]) < 0 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end return c",
        Long.class
    );
    private final ConcurrentHashMap<String, LoginThrottle.Window> counters = new ConcurrentHashMap<>();
    private final ObjectProvider<StringRedisTemplate> redisProvider;

    public LoginThrottle(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    public boolean isBlocked(String key, int maxFailures, Duration window) {
        if (key == null || maxFailures <= 0 || window == null || window.isZero() || window.isNegative()) {
            return false;
        }
        StringRedisTemplate redis = this.redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                String v = redis.opsForValue().get(PREFIX + key);
                return v != null && Long.parseLong(v) >= maxFailures;
            } catch (RuntimeException e) {
                log.debug("[login-throttle] Redis 读取失败，退回内存计数：{}", e.getMessage());
            }
        }
        LoginThrottle.Window w = this.counters.get(key);
        if (w == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - w.start >= window.toMillis()) {
            this.counters.remove(key, w);
            return false;
        }
        return w.count.get() >= maxFailures;
    }

    public void recordFailure(String key, Duration window) {
        if (key == null || window == null || window.isZero() || window.isNegative()) {
            return;
        }
        StringRedisTemplate redis = this.redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                redis.execute(INCR, List.of(PREFIX + key), String.valueOf(window.toMillis()));
                return;
            } catch (RuntimeException e) {
                log.debug("[login-throttle] Redis 写入失败，退回内存计数：{}", e.getMessage());
            }
        }
        long now = System.currentTimeMillis();
        long windowMs = window.toMillis();
        if (this.counters.size() > MAX_TRACKED) {
            this.prune(now, windowMs);
        }
        this.counters.compute(key, (k, cur) -> {
            if (cur != null && now - cur.start < windowMs) {
                cur.count.incrementAndGet();
                return cur;
            }
            LoginThrottle.Window fresh = new LoginThrottle.Window(now);
            fresh.count.incrementAndGet();
            return fresh;
        });
    }

    public void reset(String key) {
        if (key == null) {
            return;
        }
        this.counters.remove(key);
        StringRedisTemplate redis = this.redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                redis.delete(PREFIX + key);
            } catch (RuntimeException e) {
                log.debug("[login-throttle] Redis 删除失败：{}", e.getMessage());
            }
        }
    }

    private void prune(long now, long windowMs) {
        Iterator<Entry<String, LoginThrottle.Window>> it = this.counters.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().start >= windowMs) {
                it.remove();
            }
        }
    }

    // ───────────── v3.4：账号级锁定（跨 IP）与可信 IP ─────────────

    private static final String ACCT_GLOBAL = "login:fail:acctg:";
    private static final String TRUST_PREFIX = "dcim:login-trusted:";
    /** SADD + 惰性补 EXPIRE 的原子版本（ARGV[1]=ip，ARGV[2]=ttl 秒） */
    private static final RedisScript<Long> TRUST_SCRIPT = new DefaultRedisScript<>(
        "redis.call('SADD', KEYS[1], ARGV[1]) if redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end return 1",
        Long.class
    );
    private static final RedisScript<Long> ACCT_INCR = new DefaultRedisScript<>(
        "local c = redis.call('INCR', KEYS[1]) if c == 1 or redis.call('PTTL', KEYS[1]) < 0 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
            + "if c == tonumber(ARGV[2]) then redis.call('PEXPIRE', KEYS[1], ARGV[3]) end return c",
        Long.class
    );
    private final ConcurrentHashMap<String, Long> memLockedUntil = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> memTrusted = new ConcurrentHashMap<>();

    private static String acctKey(String username) {
        String u = username == null ? "" : username.trim();
        return ACCT_GLOBAL + (u.length() > 64 ? u.substring(0, 64) : u);
    }

    /** 记一次账号级失败；达到阈值时把锁定期设为 lock。返回当前计数 */
    public long recordAccountFailure(String username, Duration window, int threshold, Duration lock) {
        if (username == null || threshold <= 0) {
            return 0L;
        }
        String key = acctKey(username);
        StringRedisTemplate redis = this.redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                Long c = redis.execute(ACCT_INCR, List.of(PREFIX + key), String.valueOf(window.toMillis()), String.valueOf(threshold), String.valueOf(lock.toMillis()));
                return c == null ? 0L : c;
            } catch (RuntimeException e) {
                log.debug("[login-throttle] Redis 写入失败，退回内存计数：{}", e.getMessage());
            }
        }
        this.recordFailure(key, window);
        Window w = this.counters.get(key);
        int c = w == null ? 0 : w.count.get();
        if (c == threshold) {
            this.memLockedUntil.put(key, System.currentTimeMillis() + lock.toMillis());
        }
        return c;
    }

    public boolean isAccountLocked(String username, int threshold, Duration window) {
        if (username == null || threshold <= 0) {
            return false;
        }
        String key = acctKey(username);
        Long until = this.memLockedUntil.get(key);
        if (until != null) {
            if (until > System.currentTimeMillis()) {
                return true;
            }
            this.memLockedUntil.remove(key, until);
        }
        return this.isBlocked(key, threshold, window);
    }

    /** 剩余锁定秒数（未锁定或无法得知时为 0） */
    public long accountLockSeconds(String username) {
        String key = acctKey(username);
        StringRedisTemplate redis = this.redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                Long ms = redis.getExpire(PREFIX + key, java.util.concurrent.TimeUnit.MILLISECONDS);
                return ms == null || ms < 0 ? 0L : (ms + 999) / 1000;
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        Long until = this.memLockedUntil.get(key);
        return until == null ? 0L : Math.max(0L, (until - System.currentTimeMillis() + 999) / 1000);
    }

    /** 管理员解锁：清掉该账号的跨 IP 计数与各「账号+IP」计数 */
    public void unlockAccount(String username) {
        String key = acctKey(username);
        this.memLockedUntil.remove(key);
        this.counters.keySet().removeIf(k -> k.equals(key) || k.startsWith("login:fail:acct:" + username + ":ip:"));
        StringRedisTemplate redis = this.redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                redis.delete(PREFIX + key);
                org.springframework.data.redis.core.ScanOptions opts = org.springframework.data.redis.core.ScanOptions.scanOptions()
                    .match(PREFIX + "login:fail:acct:" + username + ":ip:*").count(500).build();
                List<String> keys = new java.util.ArrayList<>();
                try (org.springframework.data.redis.core.Cursor<String> cur = redis.scan(opts)) {
                    cur.forEachRemaining(keys::add);
                }
                if (!keys.isEmpty()) {
                    redis.delete(keys);
                }
            } catch (RuntimeException e) {
                log.warn("[login-throttle] 解锁时清理 Redis 失败：{}", e.getMessage());
            }
        }
    }

    /** 登录成功的 IP 记为该账号的可信来源；账号级锁定不影响可信 IP */
    public void trust(String username, String ip, Duration ttl) {
        if (username == null || ip == null) {
            return;
        }
        StringRedisTemplate redis = this.redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                // 修复：SADD 与 EXPIRE 分两条命令执行，expire 失败时键永不过期（可信 IP 永久有效）。
                // 改为原子 Lua：仅当 TTL 缺失或为负时补设，避免每次登录重置 TTL
                Long n = redis.execute(TRUST_SCRIPT, List.of(TRUST_PREFIX + username), ip, String.valueOf(ttl.toSeconds()));
                if (n != null) {
                    return;
                }
            } catch (RuntimeException e) {
                log.debug("[login-throttle] Redis 记录可信 IP 失败：{}", e.getMessage());
            }
        }
        if (this.memTrusted.size() > MAX_TRACKED) {
            long now = System.currentTimeMillis();
            this.memTrusted.entrySet().removeIf(e -> e.getValue() < now);
        }
        this.memTrusted.put(username + "|" + ip, System.currentTimeMillis() + ttl.toMillis());
    }

    public boolean isTrusted(String username, String ip) {
        if (username == null || ip == null) {
            return false;
        }
        StringRedisTemplate redis = this.redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                return Boolean.TRUE.equals(redis.opsForSet().isMember(TRUST_PREFIX + username, ip));
            } catch (RuntimeException e) {
                log.debug("[login-throttle] Redis 读取可信 IP 失败：{}", e.getMessage());
            }
        }
        Long until = this.memTrusted.get(username + "|" + ip);
        return until != null && until > System.currentTimeMillis();
    }

    public int trackedKeys() {
        return this.counters.size();
    }

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger();

        Window(long start) {
            this.start = start;
        }
    }
}
