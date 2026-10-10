package com.dcim.security;

import com.dcim.common.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 接口限流（v3.4，ADR-05）：令牌桶，已登录按用户、未登录按 IP 计，状态放 Redis（多实例共享）。
 *
 * <p>排在 Spring Security 之后（order -90 &gt; -100），此时已能拿到当前用户。原有的 {@link RateLimitFilter}
 * 保留为「每 IP 每分钟」的粗粒度防洪闸（默认 3000），本过滤器负责按人细分：
 * <ul>
 *   <li>普通：每用户每秒 20 次、突发 40；</li>
 *   <li>匿名：每 IP 每秒 10 次、突发 20；</li>
 *   <li>重操作（导出、导入、批量）：每用户每分钟 10 次。</li>
 * </ul>
 * Redis 不可用时退回本机内存计数；{@code RATE_LIMIT_PER_MINUTE=0} 时与原过滤器一起关闭。
 */
@Component
@Order(-90)
public class UserRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(UserRateLimitFilter.class);
    private static final String PREFIX = "dcim:rl2:";
    private static final Pattern HEAVY = Pattern.compile("^/api/.*/(export|import|template)(/.*)?$|^/api/.*/batch-[a-z-]+$");
    private static final RedisScript<List> BUCKET = new DefaultRedisScript<>(
        "if redis.replicate_commands then pcall(redis.replicate_commands) end "
            + "local rate = tonumber(ARGV[1]) local burst = tonumber(ARGV[2]) "
            + "local t = redis.call('TIME') local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000) "
            + "local d = redis.call('HMGET', KEYS[1], 'tk', 'ts') local tk = tonumber(d[1]) local ts = tonumber(d[2]) "
            + "if tk == nil or ts == nil then tk = burst ts = now end "
            + "tk = math.min(burst, tk + math.max(0, now - ts) * rate) "
            + "local ok = 0 local wait = 0 "
            + "if tk >= 1 then tk = tk - 1 ok = 1 else wait = math.ceil((1 - tk) / rate) end "
            + "redis.call('HSET', KEYS[1], 'tk', tostring(tk), 'ts', tostring(now)) "
            + "redis.call('PEXPIRE', KEYS[1], math.ceil(burst / rate) + 1000) "
            + "return {ok, wait, math.floor(tk)}",
        List.class
    );

    @Value("${dcim.rate-limit.per-minute:3000}")
    private int legacyPerMinute = 3000;
    @Value("${dcim.rate-limit.enabled:true}")
    private boolean enabled = true;
    @Value("${dcim.rate-limit.user-rps:20}")
    private double userRps = 20;
    @Value("${dcim.rate-limit.user-burst:40}")
    private int userBurst = 40;
    @Value("${dcim.rate-limit.anon-rps:10}")
    private double anonRps = 10;
    @Value("${dcim.rate-limit.anon-burst:20}")
    private int anonBurst = 20;
    @Value("${dcim.rate-limit.heavy-per-minute:10}")
    private int heavyPerMinute = 10;

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectMapper objectMapper;
    private final ClientIp clientIp;
    private final ConcurrentHashMap<String, double[]> memBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastWarn = new ConcurrentHashMap<>();

    public UserRateLimitFilter(ObjectProvider<StringRedisTemplate> redisProvider, ObjectMapper objectMapper, ClientIp clientIp) {
        this.redisProvider = redisProvider;
        this.objectMapper = objectMapper;
        this.clientIp = clientIp;
    }

    /** 判定结果：allowed、需等待毫秒 */
    record Decision(boolean allowed, long waitMs, int limit) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (!this.enabled || this.legacyPerMinute == 0) {
            chain.doFilter(request, response);
            return;
        }
        LoginUser user = CurrentUser.get();
        String path = request.getRequestURI();
        boolean heavy = HEAVY.matcher(path).matches();
        String who = user != null ? "u:" + user.username() : "ip:" + this.clientIp.resolve(request);
        Decision d;
        if (heavy && user != null) {
            d = this.take(who + ":heavy", this.heavyPerMinute / 60_000.0, this.heavyPerMinute);
        } else if (user != null) {
            d = this.take(who, this.userRps / 1000.0, this.userBurst);
        } else {
            d = this.take(who, this.anonRps / 1000.0, this.anonBurst);
        }
        if (d.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = this.lastWarn.get(who);
        if (prev == null || now - prev > 60_000L) {
            this.lastWarn.put(who, now);
            if (this.lastWarn.size() > 10_000) {
                this.lastWarn.clear();
            }
            log.warn("[rate-limit] {} 超出{}限流，{} {}", who, heavy ? "重操作" : "", request.getMethod(), path);
        }
        long retry = Math.max(1L, (d.waitMs() + 999L) / 1000L);
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retry));
        response.setHeader("X-RateLimit-Limit", String.valueOf(d.limit()));
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String msg = heavy ? "导出 / 导入 / 批量操作过于频繁，请 " + retry + " 秒后再试" : "请求过于频繁，请 " + retry + " 秒后再试";
        response.getWriter().write(this.objectMapper.writeValueAsString(ApiResult.error(429, msg)));
    }

    Decision take(String key, double ratePerMs, int burst) {
        if (ratePerMs <= 0 || burst <= 0) {
            return new Decision(true, 0, burst);
        }
        StringRedisTemplate redis = this.redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                List<?> r = redis.execute(BUCKET, List.of(PREFIX + key), String.valueOf(ratePerMs), String.valueOf(burst));
                if (r != null && r.size() >= 2) {
                    return new Decision(((Number)r.get(0)).longValue() == 1L, ((Number)r.get(1)).longValue(), burst);
                }
            } catch (RuntimeException e) {
                log.debug("[rate-limit] Redis 不可用，退回本机计数：{}", e.getMessage());
            }
        }
        return this.takeLocal(key, ratePerMs, burst);
    }

    private Decision takeLocal(String key, double ratePerMs, int burst) {
        if (this.memBuckets.size() > 50_000) {
            this.memBuckets.clear();
        }
        long now = System.currentTimeMillis();
        double[] b = this.memBuckets.computeIfAbsent(key, k -> new double[]{burst, now});
        synchronized (b) {
            b[0] = Math.min(burst, b[0] + Math.max(0, now - b[1]) * ratePerMs);
            b[1] = now;
            if (b[0] >= 1) {
                b[0] -= 1;
                return new Decision(true, 0, burst);
            }
            return new Decision(false, (long)Math.ceil((1 - b[0]) / ratePerMs), burst);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
            || path.startsWith("/api/healthz")
            || path.startsWith("/api/collector/")
            || path.startsWith("/api/monitoring/sd")
            || path.startsWith("/api/monitoring/ingest/")
            // SSE 建连豁免：每标签页仅一次低频请求，注册表自带 200 连接上限与心跳清理；
            // 若按普通请求计数，429 会触发 EventSource 全体重连风暴，反而放大压力
            || path.equals("/api/notifications/sse");
    }
}
