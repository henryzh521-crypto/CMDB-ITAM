package com.dcim.security.ldap;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.BusinessException;
import com.dcim.security.LoginUserLoader;
import com.dcim.system.entity.UserEntity;
import com.dcim.system.mapper.UserMapper;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LdapUserProvisioner {
    
    private static final Logger log = LoggerFactory.getLogger(LdapUserProvisioner.class);
    private final UserMapper userMapper;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final LoginUserLoader loader;
    private final boolean autoCreate;
    private final String defaultRole;
    private final SecureRandom random = new SecureRandom();

    public LdapUserProvisioner(
        UserMapper userMapper,
        JdbcTemplate jdbc,
        PasswordEncoder encoder,
        LoginUserLoader loader,
        @Value("${dcim.ldap.auto-create:true}") boolean autoCreate,
        @Value("${dcim.ldap.default-role:VIEWER}") String defaultRole
    ) {
        this.userMapper = userMapper;
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.loader = loader;
        this.autoCreate = autoCreate;
        this.defaultRole = defaultRole;
    }

    @Transactional
    public UserEntity sync(UserEntity existing, LdapAuthService.DirUser d) {
        UserEntity u = existing;
        boolean created = false;
        if (existing == null) {
            if (!this.autoCreate) {
                throw BusinessException.unauthorized("目录账号尚未在平台开通，请联系管理员");
            }

            u = new UserEntity();
            u.setUsername(d.username());
            byte[] b = new byte[32];
            this.random.nextBytes(b);
            u.setPasswordHash(this.encoder.encode(Base64.getEncoder().encodeToString(b)));
            u.setStatus(1);
            u.setMustChangePwd(false);
            u.setAuthSource("LDAP");
            u.setCreatedAt(LocalDateTime.now());
            created = true;
        }

        u.setName(trim(d.name(), 50, d.username()));
        if (d.email() != null) {
            u.setEmail(trim(d.email(), 100, null));
        }

        if (d.phone() != null) {
            u.setPhone(trim(d.phone(), 30, null));
        }

        if (d.department() != null) {
            u.setDepartment(trim(d.department(), 100, null));
        }

        u.setUpdatedAt(LocalDateTime.now());
        if (created) {
            this.userMapper.insert(u);
        } else {
            this.userMapper.update(u, Wrappers.<UserEntity>lambdaUpdate().eq(UserEntity::getId, u.getId()));
        }

        Set<String> roles = d.mappedRoles();
        if (!roles.isEmpty()) {
            this.jdbc.update("DELETE FROM user_role WHERE user_id = ?", new Object[]{u.getId()});

            for (String r : roles) {
                this.addRole(u.getId(), r);
            }
        } else if (created) {
            this.addRole(u.getId(), this.defaultRole);
        }

        if (created) {
            log.info("[ldap] 自动开通目录账号 {}（角色 {}）", u.getUsername(), roles.isEmpty() ? this.defaultRole : roles);
        }

        this.loader.evict(u.getUsername());
        return u;
    }

    private void addRole(Long userId, String role) {
        if (Boolean.TRUE
            .equals(
                this.jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM roles WHERE role_code = ? AND deleted_at IS NULL)", Boolean.class, new Object[]{role})
            )) {
            this.jdbc.update("INSERT INTO user_role (user_id, role_code) VALUES (?, ?) ON CONFLICT DO NOTHING", new Object[]{userId, role});
        } else {
            log.warn("[ldap] 角色 {} 不存在，已跳过（检查 dcim.ldap.default-role / group-role-map）", role);
        }
    }

    private static String trim(String s, int n, String def) {
        if (s != null && !s.isBlank()) {
            return s.length() <= n ? s : s.substring(0, n);
        } else {
            return def;
        }
    }
}
