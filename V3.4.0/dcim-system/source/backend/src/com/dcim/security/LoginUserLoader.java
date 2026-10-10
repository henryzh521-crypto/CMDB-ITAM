package com.dcim.security;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.system.entity.RoleActionPermEntity;
import com.dcim.system.entity.RoleDataScopeEntity;
import com.dcim.system.entity.RoleFieldPermEntity;
import com.dcim.system.entity.RoleModulePermEntity;
import com.dcim.system.entity.UserEntity;
import com.dcim.system.entity.UserRoleEntity;
import com.dcim.system.mapper.RoleActionPermMapper;
import com.dcim.system.mapper.RoleDataScopeMapper;
import com.dcim.system.mapper.RoleFieldPermMapper;
import com.dcim.system.mapper.RoleModulePermMapper;
import com.dcim.system.mapper.UserMapper;
import com.dcim.system.mapper.UserRoleMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LoginUserLoader {
    
    private static final Logger log = LoggerFactory.getLogger(LoginUserLoader.class);
    private static final String CACHE_PREFIX = "dcim:loginuser:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30L);
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleModulePermMapper modulePermMapper;
    private final RoleActionPermMapper actionPermMapper;
    private final RoleDataScopeMapper dataScopeMapper;
    private final RoleFieldPermMapper fieldPermMapper;
    private final ModuleRegistry moduleRegistry;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public LoginUserLoader(
        UserMapper userMapper,
        UserRoleMapper userRoleMapper,
        RoleModulePermMapper modulePermMapper,
        RoleActionPermMapper actionPermMapper,
        RoleDataScopeMapper dataScopeMapper,
        RoleFieldPermMapper fieldPermMapper,
        ModuleRegistry moduleRegistry,
        ObjectProvider<StringRedisTemplate> redisTemplateProvider,
        ObjectMapper objectMapper,
        JdbcTemplate jdbc
    ) {
        this.jdbc = jdbc;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.modulePermMapper = modulePermMapper;
        this.actionPermMapper = actionPermMapper;
        this.dataScopeMapper = dataScopeMapper;
        this.fieldPermMapper = fieldPermMapper;
        this.moduleRegistry = moduleRegistry;
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapper = objectMapper;
    }

    public LoginUser load(String username) {
        StringRedisTemplate redisTemplate = (StringRedisTemplate)this.redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            String cacheKey = "dcim:loginuser:" + username;

            try {
                String cached = (String)redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    return this.deserialize(cached);
                }
            } catch (Exception var6) {
                log.warn("[cache] Redis 读取失败，回退到 DB: {}", var6.getMessage());
            }
        }

        LoginUser user = this.loadFromDb(username);
        if (user != null && redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set("dcim:loginuser:" + username, this.serialize(user), CACHE_TTL);
            } catch (Exception var5) {
                log.warn("[cache] Redis 写入失败: {}", var5.getMessage());
            }
        }

        return user;
    }

    public void evict(String username) {
        StringRedisTemplate redisTemplate = (StringRedisTemplate)this.redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                redisTemplate.delete("dcim:loginuser:" + username);
            } catch (Exception var4) {
                log.warn("[cache] Redis 删除失败: {}", var4.getMessage());
            }
        }
    }

    @EventListener({ApplicationReadyEvent.class})
    public void evictOnStartup() {
        this.evictAll();
    }

    public void evictAll() {
        StringRedisTemplate redisTemplate = (StringRedisTemplate)this.redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                // SCAN 分批删除：KEYS 在大键空间下会阻塞 Redis 主线程
                deleteByPattern(redisTemplate, "dcim:loginuser:*");
            } catch (Exception var3) {
                log.warn("[cache] Redis 批量删除失败: {}", var3.getMessage());
            }
        }
    }

    /** 以 SCAN 游标分批删除匹配的键（每批 500），替代阻塞式 KEYS。 */
    static long deleteByPattern(StringRedisTemplate redis, String pattern) {
        long n = 0L;
        List<String> batch = new ArrayList<>();
        try (Cursor<String> cur = redis.scan(ScanOptions.scanOptions().match(pattern).count(500L).build())) {
            while (cur.hasNext()) {
                batch.add(cur.next());
                if (batch.size() >= 500) {
                    n += nz(redis.delete(batch));
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            n += nz(redis.delete(batch));
        }
        return n;
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private long tokensValidAfterOf(String username) {
        try {
            List<Long> rows = this.jdbc.queryForList(
                "SELECT COALESCE((EXTRACT(EPOCH FROM token_valid_after) * 1000)::bigint, 0) FROM users WHERE username = ? AND deleted_at IS NULL",
                Long.class,
                username
            );
            return rows.isEmpty() || rows.get(0) == null ? 0L : rows.get(0);
        } catch (RuntimeException e) {
            // 升级窗口（V43 未执行）时列不存在：按无水位线处理，不阻断登录
            log.debug("[auth] 读取 token_valid_after 失败：{}", e.getMessage());
            return 0L;
        }
    }

    private LoginUser loadFromDb(String username) {
        UserEntity u = (UserEntity)this.userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, username));
        if (u != null && u.getStatus() != null && u.getStatus() == 1) {
            List<String> roles = this.userRoleMapper
                .selectList(Wrappers.<UserRoleEntity>lambdaQuery().eq(UserRoleEntity::getUserId, u.getId()))
                .stream()
                .map(UserRoleEntity::getRoleCode)
                .distinct()
                .toList();
            boolean isAdmin = roles.contains("ADMIN");
            Map<String, LoginUser.ModulePerm> modulePerms = new HashMap<>();
            Map<String, Map<String, Boolean>> actionPerms = new HashMap<>();
            Map<String, Map<String, Object>> dataScopes = new LinkedHashMap<>();
            Map<String, Map<String, boolean[]>> roleAccess = new HashMap<>();
            Map<String, Map<String, Map<String, Object>>> roleFields = new HashMap<>();

            // 修复：原实现对每个角色各发 4 条 SQL（N+1），无 Redis 缓存时每个请求都要重查权限。
            // 改为每张权限表一次 IN 查询后在内存中合并，结果与逐角色查询完全一致
            for (RoleModulePermEntity p : roles.isEmpty() ? List.<RoleModulePermEntity>of()
                : this.modulePermMapper.selectList(Wrappers.<RoleModulePermEntity>lambdaQuery().in(RoleModulePermEntity::getRoleCode, roles))) {
                modulePerms.merge(
                    p.getModule(),
                    LoginUser.ModulePerm.of(Boolean.TRUE.equals(p.getCanRead()), Boolean.TRUE.equals(p.getCanWrite())),
                    (a, b) -> LoginUser.ModulePerm.of(a.read() || b.read(), a.write() || b.write())
                );
                roleAccess.computeIfAbsent(p.getRoleCode(), k -> new HashMap<>())
                    .put(p.getModule(), new boolean[]{Boolean.TRUE.equals(p.getCanRead()), Boolean.TRUE.equals(p.getCanWrite())});
            }

            for (RoleActionPermEntity a : roles.isEmpty() ? List.<RoleActionPermEntity>of()
                : this.actionPermMapper.selectList(Wrappers.<RoleActionPermEntity>lambdaQuery().in(RoleActionPermEntity::getRoleCode, roles))) {
                actionPerms.merge(a.getModule(), this.asBoolMap(a.getActions()), this::mergeBoolMaps);
            }

            for (RoleDataScopeEntity d : roles.isEmpty() ? List.<RoleDataScopeEntity>of()
                : this.dataScopeMapper.selectList(Wrappers.<RoleDataScopeEntity>lambdaQuery().in(RoleDataScopeEntity::getRoleCode, roles))) {
                // 多角色取最宽范围（v3.2 用 putIfAbsent，结果取决于角色遍历顺序）
                dataScopes.merge(d.getModule(), d.getScope() == null ? Map.of() : d.getScope(), DataScopeService::widest);
            }

            for (RoleFieldPermEntity f : roles.isEmpty() ? List.<RoleFieldPermEntity>of()
                : this.fieldPermMapper.selectList(Wrappers.<RoleFieldPermEntity>lambdaQuery().in(RoleFieldPermEntity::getRoleCode, roles))) {
                if (f.getFields() != null) {
                    roleFields.computeIfAbsent(f.getRoleCode(), k -> new HashMap<>()).put(f.getModule(), f.getFields());
                }
            }

            // v3.4：字段权限按「可见 / 可编辑取最宽」合并（v3.3 取并集＝最严，且只存不用），管理员不受限
            Map<String, Map<String, Object>> fieldPerms = isAdmin
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(com.dcim.security.field.FieldPolicyService.merge(roleAccess, roleFields));

            if (isAdmin) {
                for (ModuleRegistry.ModuleDef m : this.moduleRegistry.list()) {
                    modulePerms.put(m.code(), LoginUser.ModulePerm.of(true, true));
                }
            }

            return new LoginUser(
                u.getUsername(),
                u.getName(),
                Boolean.TRUE.equals(u.getMustChangePwd()),
                roles,
                isAdmin,
                Map.copyOf(modulePerms),
                this.deepCopy(actionPerms),
                this.deepCopyObj(dataScopes),
                this.deepCopyObj(fieldPerms),
                u.getDepartment(),
                this.tokensValidAfterOf(u.getUsername())
            );
        } else {
            return null;
        }
    }

    private String serialize(LoginUser user) throws JsonProcessingException {
        Map<String, Object> map = new HashMap<>();
        map.put("username", user.username());
        map.put("name", user.name());
        map.put("mustChangePwd", user.mustChangePwd());
        map.put("roles", user.roles());
        map.put("isAdmin", user.isAdmin());
        map.put("modulePerms", user.modulePerms());
        map.put("actionPerms", user.actionPerms());
        map.put("dataScopes", user.dataScopes());
        map.put("fieldPerms", user.fieldPerms());
        map.put("department", user.department());
        map.put("tokensValidAfter", user.tokensValidAfter());
        return this.objectMapper.writeValueAsString(map);
    }

    private LoginUser deserialize(String json) throws JsonProcessingException {
        return (LoginUser)this.objectMapper.readValue(json, LoginUser.class);
    }

    private Map<String, Boolean> asBoolMap(Map<String, Object> raw) {
        Map<String, Boolean> out = new HashMap<>();
        if (raw != null) {
            raw.forEach((k, v) -> out.put(k, Boolean.TRUE.equals(v) || "true".equals(String.valueOf(v))));
        }

        return out;
    }

    private Map<String, Boolean> mergeBoolMaps(Map<String, Boolean> a, Map<String, Boolean> b) {
        Map<String, Boolean> merged = new HashMap<>(a);
        b.forEach((k, v) -> merged.merge(k, v, Boolean::logicalOr));
        return merged;
    }

    private List<Object> union(List<?> a, List<?> b) {
        List<Object> out = new ArrayList<>((Collection<? extends Object>)a);
        b.forEach(x -> {
            if (!out.contains(x)) {
                out.add(x);
            }
        });
        return out;
    }

    private <V> Map<String, Map<String, V>> deepCopy(Map<String, Map<String, V>> src) {
        Map<String, Map<String, V>> out = new LinkedHashMap<>();
        src.forEach((k, v) -> out.put(k, Map.copyOf((Map<? extends String, ? extends V>)v)));
        return Map.copyOf(out);
    }

    private Map<String, Map<String, Object>> deepCopyObj(Map<String, Map<String, Object>> src) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        src.forEach((k, v) -> out.put(k, Map.copyOf((Map<? extends String, ? extends Object>)v)));
        return Map.copyOf(out);
    }
}
