package com.dcim.security;

import java.util.LinkedHashMap;
import com.dcim.common.BusinessException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 授权“天花板”校验（v3.3，修复纵向越权）。
 *
 * <p>v3.2 中只要角色拥有 system 模块写权限（例如给 IT 服务台开的“用户管理员”角色），
 * 就能：给自己或他人授予 ADMIN 角色、重置 admin 密码、停用 admin、把任意角色权限改成全开。
 * 规则（参考 ServiceNow / Keycloak 的委派管理模型）：
 * <ol>
 *   <li>只有管理员能授予 ADMIN 角色、修改/停用/重置管理员账号；</li>
 *   <li>非管理员只能授予「权限不超过自己」的角色（模块读写、动作权限逐项比较）；</li>
 *   <li>非管理员不能修改内置角色，新建/修改角色时授予的权限不能超过自己。</li>
 * </ol>
 */
@Component
public class PrivilegeGuard {
    public static final String ADMIN_ROLE = "ADMIN";
    private final JdbcTemplate jdbc;

    public PrivilegeGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 目标账号当前是否为管理员（持有 ADMIN 角色）。 */
    public boolean isAdminAccount(String username) {
        Long n = this.jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_role ur JOIN users u ON u.id = ur.user_id WHERE u.username = ? AND u.deleted_at IS NULL AND ur.role_code = 'ADMIN'",
            Long.class,
            username
        );
        return n != null && n > 0L;
    }

    /** 非管理员不能修改 / 停用 / 重置 / 删除管理员账号。 */
    public void assertCanManageUser(LoginUser actor, String targetUsername) {
        if (actor != null && actor.isAdmin()) {
            return;
        }
        if ("admin".equals(targetUsername) || this.isAdminAccount(targetUsername)) {
            throw BusinessException.forbidden("仅管理员可修改管理员账号");
        }
    }

    /** 非管理员授予的角色不能超过自己的权限。 */
    public void assertCanGrantRoles(LoginUser actor, Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty() || (actor != null && actor.isAdmin())) {
            return;
        }
        if (actor == null) {
            throw BusinessException.unauthorized("未登录或会话已过期");
        }
        for (String rc : new LinkedHashSet<>(roleCodes)) {
            if (rc == null) {
                continue;
            }
            if (ADMIN_ROLE.equals(rc.trim())) {
                throw BusinessException.forbidden("仅管理员可授予「系统管理员」角色");
            }
            List<Map<String, Object>> perms = this.jdbc.queryForList(
                "SELECT module, can_read, can_write FROM role_module_perm WHERE role_code = ?", rc.trim()
            );
            Map<String, Map<String, Object>> rolePerms = new LinkedHashMap<>();
            for (Map<String, Object> p : perms) {
                this.assertModuleWithin(actor, String.valueOf(p.get("module")),
                    Boolean.TRUE.equals(p.get("can_read")), Boolean.TRUE.equals(p.get("can_write")), "角色 " + rc);
                rolePerms.put(String.valueOf(p.get("module")), Map.of("read", Boolean.TRUE.equals(p.get("can_read")), "write", Boolean.TRUE.equals(p.get("can_write"))));
            }
            // v3.4：授予已有角色时，操作（删除）、数据范围、字段规则也不能超出自己
            for (Map<String, Object> a : this.jdbc.queryForList("SELECT module, actions::text AS actions FROM role_action_perm WHERE role_code = ?", rc.trim())) {
                String module = String.valueOf(a.get("module"));
                String json = String.valueOf(a.get("actions"));
                if (json.replace(" ", "").contains("\"delete\":true") && !actorCanDelete(actor, module)) {
                    throw BusinessException.forbidden("角色 " + rc + " 含有你未拥有的「" + module + "」删除权限，需管理员授予");
                }
            }
            this.assertScopesAndFieldsWithin(actor, "角色 " + rc, rolePerms, this.loadScopes(rc.trim()), this.loadFields(rc.trim()));
        }
    }

    private Map<String, Map<String, Object>> loadScopes(String roleCode) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map<String, Object> r : this.jdbc.queryForList("SELECT module, scope->>'type' AS t FROM role_data_scope WHERE role_code = ?", roleCode)) {
            out.put(String.valueOf(r.get("module")), Map.of("type", String.valueOf(r.get("t"))));
        }
        return out;
    }

    private Map<String, Map<String, Object>> loadFields(String roleCode) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map<String, Object> r : this.jdbc.queryForList(
            "SELECT module, COALESCE(fields->'hidden', '[]'::jsonb)::text AS h, COALESCE(fields->'readonly', '[]'::jsonb)::text AS r FROM role_field_perm WHERE role_code = ?",
            roleCode
        )) {
            out.put(String.valueOf(r.get("module")), Map.of("hidden", jsonList(String.valueOf(r.get("h"))), "readonly", jsonList(String.valueOf(r.get("r")))));
        }
        return out;
    }

    private static List<String> jsonList(String json) {
        List<String> out = new ArrayList<>();
        for (String part : json.replace("[", "").replace("]", "").split(",")) {
            String v = part.trim().replace("\"", "");
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }

    private static boolean actorCanDelete(LoginUser actor, String module) {
        if (actor == null) {
            return false;
        }
        if (actor.isAdmin()) {
            return true;
        }
        LoginUser.ModulePerm p = actor.modulePerms().get(module);
        Map<String, Boolean> acts = actor.actionPerms() == null ? null : actor.actionPerms().get(module);
        return p != null && p.write() && acts != null && Boolean.TRUE.equals(acts.get("delete"));
    }

    /**
     * 数据范围与字段规则的天花板：
     * <ul>
     *   <li>角色在某模块上的数据范围不能比操作者宽（未配置视为「全部」）；</li>
     *   <li>操作者在某模块上隐藏 / 只读的字段，角色也必须隐藏 / 不可写（字段层只能更严）。</li>
     * </ul>
     */
    public void assertScopesAndFieldsWithin(LoginUser actor, String what, Map<String, Map<String, Object>> modulePerms,
                                            Map<String, ? extends Object> dataScopes, Map<String, Map<String, Object>> fieldPerms) {
        if (actor == null || actor.isAdmin() || modulePerms == null) {
            return;
        }
        List<String> over = new ArrayList<>();
        modulePerms.forEach((module, m) -> {
            boolean access = m != null && (Boolean.TRUE.equals(m.get("read")) || Boolean.TRUE.equals(m.get("write")));
            if (!access) {
                return;
            }
            Object cfg = dataScopes == null ? null : dataScopes.get(module);
            Object type = cfg instanceof Map<?, ?> cm ? cm.get("type") : null;
            DataScopeService.Scope roleScope = DataScopeService.parse(type);
            Map<String, Object> mine = actor.dataScopes() == null ? null : actor.dataScopes().get(module);
            DataScopeService.Scope actorScope = DataScopeService.parse(mine == null ? null : mine.get("type"));
            if (roleScope.ordinal() > actorScope.ordinal()) {
                over.add("「" + module + "」数据范围 " + roleScope + "（你的范围是 " + actorScope + "）");
            }
            Map<String, Object> myFields = actor.fieldPerms() == null ? null : actor.fieldPerms().get(module);
            if (myFields != null) {
                com.dcim.security.field.FieldPolicy minePolicy = com.dcim.security.field.FieldPolicy.of(
                    (java.util.Collection<?>)myFields.getOrDefault("hidden", List.of()), (java.util.Collection<?>)myFields.getOrDefault("readonly", List.of()));
                Map<String, Object> rf = fieldPerms == null ? null : fieldPerms.get(module);
                com.dcim.security.field.FieldPolicy rolePolicy = rf == null ? com.dcim.security.field.FieldPolicy.NONE
                    : com.dcim.security.field.FieldPolicy.of((java.util.Collection<?>)rf.getOrDefault("hidden", List.of()), (java.util.Collection<?>)rf.getOrDefault("readonly", List.of()));
                for (String f : minePolicy.hidden()) {
                    if (!rolePolicy.isHidden(f)) {
                        over.add("「" + module + "」字段 " + f + " 对你隐藏，角色里也须隐藏");
                    }
                }
                for (String f : minePolicy.readonly()) {
                    if (!rolePolicy.isLocked(f)) {
                        over.add("「" + module + "」字段 " + f + " 对你只读，角色里也须只读或隐藏");
                    }
                }
            }
        });
        if (!over.isEmpty()) {
            throw BusinessException.forbidden(what + " 超出你自身的权限：" + String.join("；", over));
        }
    }

    /** 非管理员新建 / 修改角色时，授予的权限不能超过自己；且不能修改内置角色。 */
    public void assertCanSaveRole(LoginUser actor, String roleCode, Map<String, Map<String, Object>> modulePerms,
                                  Map<String, Map<String, Object>> actionPerms, boolean exists) {
        if (actor != null && actor.isAdmin()) {
            return;
        }
        if (actor == null) {
            throw BusinessException.unauthorized("未登录或会话已过期");
        }
        if (ADMIN_ROLE.equals(roleCode)) {
            throw BusinessException.forbidden("仅管理员可修改「系统管理员」角色");
        }
        if (exists) {
            Boolean builtIn = this.jdbc.query(
                "SELECT built_in FROM roles WHERE role_code = ?", rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, roleCode
            );
            if (Boolean.TRUE.equals(builtIn)) {
                throw BusinessException.forbidden("仅管理员可修改内置角色");
            }
        }
        if (modulePerms != null) {
            modulePerms.forEach((module, m) -> {
                boolean r = m != null && Boolean.TRUE.equals(m.get("read"));
                boolean w = m != null && Boolean.TRUE.equals(m.get("write"));
                this.assertModuleWithin(actor, module, r, w, "角色 " + roleCode);
            });
        }
        if (actionPerms != null) {
            List<String> over = new ArrayList<>();
            actionPerms.forEach((module, acts) -> {
                if (acts == null) {
                    return;
                }
                Map<String, Boolean> mine = actor.actionPerms().get(module);
                acts.forEach((act, v) -> {
                    boolean granted = Boolean.TRUE.equals(v) || "true".equals(String.valueOf(v));
                    // v3.4：删除可配置，但只能授出自己拥有的删除权限
                    boolean overDelete = "delete".equals(act) && !actorCanDelete(actor, module);
                    if (granted && (overDelete || (mine != null && Boolean.FALSE.equals(mine.get(act))))) {
                        over.add(module + "." + act);
                    }
                });
            });
            if (!over.isEmpty()) {
                throw BusinessException.forbidden("不能授予超出自身的操作权限：" + String.join("、", over));
            }
        }
    }

    private void assertModuleWithin(LoginUser actor, String module, boolean read, boolean write, String what) {
        LoginUser.ModulePerm mine = actor.modulePerms().get(module);
        boolean canRead = mine != null && (mine.read() || mine.write());
        boolean canWrite = mine != null && mine.write();
        if ((write && !canWrite) || (read && !canRead)) {
            throw BusinessException.forbidden(what + " 含有超出你自身权限的模块「" + module + "」" + (write ? "写" : "读") + "权限，需管理员授予");
        }
    }

    /** 便于导入等批量场景复用。 */
    public static Set<String> normalize(Collection<String> roleCodes) {
        Set<String> out = new LinkedHashSet<>();
        if (roleCodes != null) {
            for (String r : roleCodes) {
                if (r != null && !r.isBlank()) {
                    out.add(r.trim());
                }
            }
        }
        return out;
    }
}
