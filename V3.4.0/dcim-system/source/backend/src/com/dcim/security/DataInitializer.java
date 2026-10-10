package com.dcim.security;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.metrics.MetricStore;
import com.dcim.system.entity.RoleActionPermEntity;
import com.dcim.system.entity.RoleDataScopeEntity;
import com.dcim.system.entity.RoleEntity;
import com.dcim.system.entity.RoleFieldPermEntity;
import com.dcim.system.entity.RoleModulePermEntity;
import com.dcim.system.entity.UserEntity;
import com.dcim.system.entity.UserRoleEntity;
import com.dcim.system.mapper.RoleActionPermMapper;
import com.dcim.system.mapper.RoleDataScopeMapper;
import com.dcim.system.mapper.RoleFieldPermMapper;
import com.dcim.system.mapper.RoleMapper;
import com.dcim.system.mapper.RoleModulePermMapper;
import com.dcim.system.mapper.UserMapper;
import com.dcim.system.mapper.UserRoleMapper;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataInitializer implements ApplicationRunner {
    
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final List<String> ACTIONS = List.of("create", "update", "delete", "import", "export");
    private static final String DEV_INITIAL_PASSWORD = "admin123";
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RoleModulePermMapper modulePermMapper;
    private final RoleActionPermMapper actionPermMapper;
    private final RoleDataScopeMapper dataScopeMapper;
    private final RoleFieldPermMapper fieldPermMapper;
    private final ModuleRegistry moduleRegistry;
    private final PasswordEncoder passwordEncoder;
    private final MetricStore metricStore;
    private final Environment environment;
    @Value("${dcim.auth.admin-initial-password:}")
    private String adminInitialPassword;

    @Transactional
    public void run(ApplicationArguments args) {
        try {
            this.metricStore.ensureSchema();
        } catch (Exception var9) {
            log.warn("[init] MetricStore.ensureSchema 失败（稍后写入时重试）: {}", var9.getMessage());
        }

        Long count = this.userMapper.selectCount(null);
        if (count != null && count > 0L) {
            log.info("[init] users 表已有 {} 个用户，跳过初始化", count);
        } else {
            String initialPassword = this.resolveInitialPassword();
            UserEntity admin = new UserEntity();
            admin.setUsername("admin");
            admin.setName("系统管理员");
            admin.setPasswordHash(this.passwordEncoder.encode(initialPassword));
            admin.setStatus(1);
            admin.setMustChangePwd(true);
            this.userMapper.insert(admin);
            UserRoleEntity bind = new UserRoleEntity();
            bind.setUserId(admin.getId());
            bind.setRoleCode("ADMIN");
            this.userRoleMapper.insert(bind);
            boolean leaderExists = this.roleMapper.selectCount(Wrappers.<RoleEntity>lambdaQuery().eq(RoleEntity::getRoleCode, "LEADER")) > 0L;

            for (String roleCode : leaderExists ? List.of("ADMIN", "LEADER") : List.of("ADMIN")) {
                this.clearRolePerms(roleCode);
            }

            for (ModuleRegistry.ModuleDef m : this.moduleRegistry.list()) {
                this.seedAdminPerms(m.code());
                if (leaderExists) {
                    this.seedLeaderPerms(m.code());
                }
            }

            log.info("[init] 已创建内置管理员 admin 并写入内置角色权限（must_change_pwd=true，首登强制改密）");
        }
    }

    private String resolveInitialPassword() {
        String configured = this.adminInitialPassword == null ? "" : this.adminInitialPassword.trim();
        if (!configured.isEmpty()) {
            log.info("[init] 内置管理员初始口令取自 dcim.auth.admin-initial-password");
            return configured;
        } else {
            boolean prod = false;

            for (String p : this.environment.getActiveProfiles()) {
                if ("prod".equalsIgnoreCase(p)) {
                    prod = true;
                    break;
                }
            }

            if (!prod) {
                return "admin123";
            } else {
                String generated = randomPassword();
                log.warn("[init] prod 未配置 dcim.auth.admin-initial-password，已为内置 admin 随机生成初始口令：{}（首登强制改密；建议改完后把该口令从本条日志中抹除）", generated);
                return generated;
            }
        }
    }

    private static String randomPassword() {
        String alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(20);

        for (int i = 0; i < 20; i++) {
            sb.append(
                "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                    .charAt(rnd.nextInt("abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".length()))
            );
        }

        return sb.toString();
    }

    private void clearRolePerms(String roleCode) {
        this.modulePermMapper.delete(Wrappers.<RoleModulePermEntity>lambdaQuery().eq(RoleModulePermEntity::getRoleCode, roleCode));
        this.actionPermMapper.delete(Wrappers.<RoleActionPermEntity>lambdaQuery().eq(RoleActionPermEntity::getRoleCode, roleCode));
        this.dataScopeMapper.delete(Wrappers.<RoleDataScopeEntity>lambdaQuery().eq(RoleDataScopeEntity::getRoleCode, roleCode));
        this.fieldPermMapper.delete(Wrappers.<RoleFieldPermEntity>lambdaQuery().eq(RoleFieldPermEntity::getRoleCode, roleCode));
    }

    private void seedAdminPerms(String module) {
        this.insertModulePerm("ADMIN", module, true, true);
        Map<String, Object> actions = new HashMap<>();
        ACTIONS.forEach(a -> actions.put(a, true));
        this.insertActionPerm("ADMIN", module, actions);
        this.insertDataScope("ADMIN", module, Map.of("type", "ALL"));
        this.insertFieldPerm("ADMIN", module, Map.of());
    }

    private void seedLeaderPerms(String module) {
        this.insertModulePerm("LEADER", module, true, false);
        Map<String, Object> actions = new HashMap<>();
        ACTIONS.forEach(a -> actions.put(a, !"delete".equals(a)));
        this.insertActionPerm("LEADER", module, actions);
        this.insertDataScope("LEADER", module, Map.of("type", "ALL"));
        this.insertFieldPerm("LEADER", module, Map.of());
    }

    private void insertModulePerm(String roleCode, String module, boolean read, boolean write) {
        RoleModulePermEntity e = new RoleModulePermEntity();
        e.setRoleCode(roleCode);
        e.setModule(module);
        e.setCanRead(read);
        e.setCanWrite(write);
        this.modulePermMapper.insert(e);
    }

    private void insertActionPerm(String roleCode, String module, Map<String, Object> actions) {
        RoleActionPermEntity e = new RoleActionPermEntity();
        e.setRoleCode(roleCode);
        e.setModule(module);
        e.setActions(actions);
        this.actionPermMapper.insert(e);
    }

    private void insertDataScope(String roleCode, String module, Map<String, Object> scope) {
        RoleDataScopeEntity e = new RoleDataScopeEntity();
        e.setRoleCode(roleCode);
        e.setModule(module);
        e.setScope(scope);
        this.dataScopeMapper.insert(e);
    }

    private void insertFieldPerm(String roleCode, String module, Map<String, Object> fields) {
        RoleFieldPermEntity e = new RoleFieldPermEntity();
        e.setRoleCode(roleCode);
        e.setModule(module);
        e.setFields(fields);
        this.fieldPermMapper.insert(e);
    }

    
    public DataInitializer(
        final UserMapper userMapper,
        final UserRoleMapper userRoleMapper,
        final RoleMapper roleMapper,
        final RoleModulePermMapper modulePermMapper,
        final RoleActionPermMapper actionPermMapper,
        final RoleDataScopeMapper dataScopeMapper,
        final RoleFieldPermMapper fieldPermMapper,
        final ModuleRegistry moduleRegistry,
        final PasswordEncoder passwordEncoder,
        final MetricStore metricStore,
        final Environment environment
    ) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.modulePermMapper = modulePermMapper;
        this.actionPermMapper = actionPermMapper;
        this.dataScopeMapper = dataScopeMapper;
        this.fieldPermMapper = fieldPermMapper;
        this.moduleRegistry = moduleRegistry;
        this.passwordEncoder = passwordEncoder;
        this.metricStore = metricStore;
        this.environment = environment;
    }
}
