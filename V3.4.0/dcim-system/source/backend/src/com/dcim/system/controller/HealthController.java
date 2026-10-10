package com.dcim.system.controller;

import com.dcim.common.ApiResult;
import com.dcim.security.CurrentUser;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康探针（Caddy 上游探活 / Docker healthcheck / Prometheus blackbox）。
 *
 * <p>v3.3 调整：
 * <ul>
 *   <li>只有数据库不可用才返回 503。Redis（会话 / 限流 / 缓存，均有降级路径）不可用或连接池排队
 *       只标记 degraded 并返回 200——v3.2 中 Redis 一抖动，Caddy 就把后端整体摘除，全站 502；</li>
 *   <li>JVM 内存、连接池等明细只对本机探针（容器内 healthcheck）与已登录用户返回，匿名外部访问只给状态；</li>
 *   <li>Redis 探测连接用完即关；</li>
 *   <li>v3.3.1：返回后端版本号（明细里另有构建日期与 jar 路径），用于核对实际运行的是哪个包。</li>
 * </ul>
 */
@RestController
@RequestMapping({"/api"})
public class HealthController {
    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    private final DataSource dataSource;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactory;
    @Value("${dcim.storage.type:local}")
    private String storageType;

    public HealthController(DataSource dataSource, ObjectProvider<RedisConnectionFactory> redisConnectionFactory) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping({"/healthz"})
    public ResponseEntity<ApiResult<Map<String, Object>>> healthz(HttpServletRequest request) {
        boolean detailed = isLoopback(request.getRemoteAddr()) || CurrentUser.get() != null;
        Map<String, Object> data = new LinkedHashMap<>();
        long dbStart = System.currentTimeMillis();
        boolean dbOk = this.checkDatabase();
        long dbMs = System.currentTimeMillis() - dbStart;
        long redisStart = System.currentTimeMillis();
        Boolean redisOk = this.checkRedis();
        long redisMs = System.currentTimeMillis() - redisStart;
        Map<String, Object> pool = new LinkedHashMap<>();
        boolean poolOk = this.checkConnectionPool(pool);
        boolean degraded = Boolean.FALSE.equals(redisOk) || !poolOk;

        data.put("status", !dbOk ? "down" : (degraded ? "degraded" : "up"));
        data.put("version", com.dcim.common.BuildInfo.version());
        data.put("db", dbOk ? "up" : "down");
        data.put("redis", redisOk == null ? "disabled" : (redisOk ? "up" : "down"));
        data.put("connectionPool", poolOk ? "up" : "saturated");
        if (detailed) {
            data.put("dbLatencyMs", dbMs);
            data.put("redisLatencyMs", redisMs);
            data.putAll(pool);
            Runtime runtime = Runtime.getRuntime();
            data.put("jvmMemoryUsedMB", (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L);
            data.put("jvmMemoryMaxMB", runtime.maxMemory() / 1024L / 1024L);
            data.put("storage", this.storageType);
            data.put("build", com.dcim.common.BuildInfo.buildTime());
            data.put("jar", com.dcim.common.BuildInfo.jarLocation());
        }
        if (!dbOk) {
            ApiResult<Map<String, Object>> body = new ApiResult<>(503, "服务不可用：数据库连接失败", data);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        return ResponseEntity.ok(ApiResult.ok(data));
    }

    private static boolean isLoopback(String ip) {
        return ip != null && (ip.startsWith("127.") || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip));
    }

    private boolean checkDatabase() {
        try (Connection conn = this.dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.setQueryTimeout(3);
            try (ResultSet rs = st.executeQuery("SELECT 1")) {
                return rs.next() && rs.getInt(1) == 1;
            }
        } catch (Exception e) {
            log.error("[health] 数据库探测失败: {}", e.getMessage());
            return false;
        }
    }

    /** @return null = 未配置 Redis；true/false = 探测结果 */
    private Boolean checkRedis() {
        RedisConnectionFactory factory = this.redisConnectionFactory.getIfAvailable();
        if (factory == null) {
            return null;
        }
        try (RedisConnection c = factory.getConnection()) {
            c.ping();
            return true;
        } catch (Exception e) {
            log.warn("[health] Redis 探测失败（降级运行）: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkConnectionPool(Map<String, Object> data) {
        if (this.dataSource instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null) {
            int waiting = hikari.getHikariPoolMXBean().getThreadsAwaitingConnection();
            data.put("poolActive", hikari.getHikariPoolMXBean().getActiveConnections());
            data.put("poolIdle", hikari.getHikariPoolMXBean().getIdleConnections());
            data.put("poolTotal", hikari.getHikariPoolMXBean().getTotalConnections());
            data.put("poolWaiting", waiting);
            return waiting < 10;
        }
        return true;
    }
}
