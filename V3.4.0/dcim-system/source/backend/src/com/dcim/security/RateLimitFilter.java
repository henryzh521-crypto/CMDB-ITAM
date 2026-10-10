package com.dcim.security;

import com.dcim.common.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
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

@Component
@Order(-2147483647)
public class RateLimitFilter extends OncePerRequestFilter {
    
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final Duration WINDOW = Duration.ofMinutes(1L);
    private static final RedisScript<Long> INCR_SCRIPT = new DefaultRedisScript(
        "local c = redis.call('INCR', KEYS[1]) if c == 1 or redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end return c",
        Long.class
    );
    // 修复：默认值与 UserRateLimitFilter 中同名属性（3000）及 compose 配置不一致，统一为 3000
    @Value("${dcim.rate-limit.per-minute:3000}")
    private int limitPerMinute = 3000;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;
    private final ClientIp clientIp;

    public RateLimitFilter(ObjectProvider<StringRedisTemplate> redisTemplateProvider, ObjectMapper objectMapper, ClientIp clientIp) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapper = objectMapper;
        this.clientIp = clientIp;
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        StringRedisTemplate redisTemplate = (StringRedisTemplate)this.redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            chain.doFilter(request, response);
        } else {
            String clientId = "ip:" + this.clientIp.resolve(request);
            String key = "dcim:ratelimit:" + clientId;

            try {
                if (this.limitPerMinute <= 0) {
                    chain.doFilter(request, response);
                    return;
                }

                Long current = (Long)redisTemplate.execute(INCR_SCRIPT, List.of(key), new Object[]{String.valueOf(WINDOW.toSeconds())});
                if (current != null && current > (long)this.limitPerMinute) {
                    if (current == (long)(this.limitPerMinute + 1)) {
                        log.warn("[rate-limit] 客户端 {} 超限: {}/{}", new Object[]{clientId, current, this.limitPerMinute});
                    }

                    response.setHeader("Retry-After", "60");
                    response.setStatus(429);
                    response.setContentType("application/json");
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write(this.objectMapper.writeValueAsString(ApiResult.error(429, "请求过于频繁，请稍后再试")));
                    return;
                }
            } catch (Exception var8) {
                log.warn("[rate-limit] Redis 限流失败，放行请求: {}", var8.getMessage());
            }

            chain.doFilter(request, response);
        }
    }

    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/healthz")
            || path.startsWith("/api/collector/")
            || path.startsWith("/api/monitoring/sd")
            || path.startsWith("/api/monitoring/ingest/");
    }
}
