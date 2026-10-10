package com.dcim.system.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.ModuleRegistry;
import com.dcim.security.RbacService;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import com.dcim.system.entity.RoleActionPermEntity;
import com.dcim.system.entity.RoleDataScopeEntity;
import com.dcim.system.entity.RoleEntity;
import com.dcim.system.entity.RoleFieldPermEntity;
import com.dcim.system.entity.RoleModulePermEntity;
import com.dcim.system.mapper.RoleActionPermMapper;
import com.dcim.system.mapper.RoleDataScopeMapper;
import com.dcim.system.mapper.RoleFieldPermMapper;
import com.dcim.system.mapper.RoleMapper;
import com.dcim.system.mapper.RoleModulePermMapper;
import com.dcim.system.service.RoleService;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoleController {
    private final RoleMapper roleMapper;
    private final RoleModulePermMapper modulePermMapper;
    private final RoleActionPermMapper actionPermMapper;
    private final RoleDataScopeMapper dataScopeMapper;
    private final RoleFieldPermMapper fieldPermMapper;
    private final RoleService roleService;
    private final RbacService rbacService;
    private final ModuleRegistry moduleRegistry;
    private final com.dcim.security.PrivilegeGuard privilegeGuard;

    @GetMapping({"/api/roles"})
    @RequirePerm(
        module = "system"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String keyword
    ) {
        LambdaQueryWrapper<RoleEntity> qw = new LambdaQueryWrapper<RoleEntity>().orderByAsc(RoleEntity::getId);
        if (keyword != null && !keyword.isBlank()) {
            // v3.4：按编码 / 名称模糊搜索（不区分大小写；转义 LIKE 通配符）
            String kw = "%" + keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            qw.apply("(role_code ILIKE {0} OR name ILIKE {0})", kw);
        }
        Page<RoleEntity> p = (Page<RoleEntity>)this.roleMapper.selectPage(new Page(page, Math.min(Math.max(size, 1L), 200L)), qw);
        List<String> codes = p.getRecords().stream().map(RoleEntity::getRoleCode).toList();
        Map<String, Map<String, Map<String, Object>>> modulePerms = new HashMap<>();
        Map<String, Map<String, Map<String, Object>>> dataScopes = new HashMap<>();
        Map<String, Map<String, Map<String, Object>>> fieldPerms = new HashMap<>();
        Map<String, Map<String, Map<String, Object>>> actionPerms = new HashMap<>();
        if (!codes.isEmpty()) {
            this.modulePermMapper
                .selectList(Wrappers.<RoleModulePermEntity>lambdaQuery().in(RoleModulePermEntity::getRoleCode, codes))
                .forEach(
                    e -> modulePerms.computeIfAbsent(e.getRoleCode(), k -> new LinkedHashMap<>())
                            .put(e.getModule(), Map.of("read", Boolean.TRUE.equals(e.getCanRead()), "write", Boolean.TRUE.equals(e.getCanWrite())))
                );
            this.dataScopeMapper
                .selectList(Wrappers.<RoleDataScopeEntity>lambdaQuery().in(RoleDataScopeEntity::getRoleCode, codes))
                .forEach(
                    e -> dataScopes.computeIfAbsent(e.getRoleCode(), k -> new LinkedHashMap<>())
                            .put(e.getModule(), e.getScope() == null ? Map.of() : e.getScope())
                );
            this.fieldPermMapper
                .selectList(Wrappers.<RoleFieldPermEntity>lambdaQuery().in(RoleFieldPermEntity::getRoleCode, codes))
                .forEach(
                    e -> fieldPerms.computeIfAbsent(e.getRoleCode(), k -> new LinkedHashMap<>())
                            .put(e.getModule(), e.getFields() == null ? Map.of() : e.getFields())
                );
            this.actionPermMapper
                .selectList(Wrappers.<RoleActionPermEntity>lambdaQuery().in(RoleActionPermEntity::getRoleCode, codes))
                .forEach(
                    e -> actionPerms.computeIfAbsent(e.getRoleCode(), k -> new LinkedHashMap<>())
                            .put(e.getModule(), e.getActions() == null ? Map.of() : e.getActions())
                );
        }

        List<Map<String, Object>> records = p.getRecords().stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roleCode", r.getRoleCode());
            row.put("name", r.getName());
            row.put("description", r.getDescription());
            row.put("builtIn", r.getBuiltIn());
            row.put("modulePerms", modulePerms.getOrDefault(r.getRoleCode(), Map.of()));
            row.put("dataScopes", dataScopes.getOrDefault(r.getRoleCode(), Map.of()));
            row.put("fieldPerms", fieldPerms.getOrDefault(r.getRoleCode(), Map.of()));
            row.put("actionPerms", actionPerms.getOrDefault(r.getRoleCode(), Map.of()));
            return row;
        }).toList();
        return ApiResult.ok(new PageResult<>(p.getTotal(), records));
    }

    /** v3.4：可配置字段权限的字段目录（角色编辑页使用） */
    @GetMapping({"/api/roles/field-catalog"})
    @RequirePerm(
        module = "system"
    )
    public ApiResult<Map<String, Object>> fieldCatalog() {
        Map<String, Object> out = new LinkedHashMap<>();
        com.dcim.security.field.FieldCatalog.all().forEach((module, defs) -> out.put(module, defs.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("field", d.field());
            m.put("label", d.label());
            return m;
        }).toList()));
        return ApiResult.ok(out);
    }

    @PostMapping({"/api/roles"})
    @RequirePerm(
        module = "system",
        write = true
    )
    @Audit(
        action = "CREATE",
        module = "system",
        objType = "role",
        objIdSpEL = "#req.roleCode"
    )
    public ApiResult<Void> create(@RequestBody RoleController.SaveRoleReqBody req) {
        this.validatePermModules(req);
        this.privilegeGuard.assertCanSaveRole(CurrentUser.get(), req.getRoleCode(), req.getModulePerms(), req.getActionPerms(), false);
        RoleService.SaveRoleReq sr = req.toSaveRoleReq();
        this.privilegeGuard.assertScopesAndFieldsWithin(CurrentUser.get(), "角色 " + req.getRoleCode(), sr.modulePerms(), sr.dataScopes(), sr.fieldPerms());
        this.roleService.create(req.getRoleCode(), sr);
        return ApiResult.ok();
    }

    @PutMapping({"/api/roles/{roleCode}"})
    @RequirePerm(
        module = "system",
        write = true
    )
    @Audit(
        action = "UPDATE",
        module = "system",
        objType = "role",
        objIdSpEL = "#roleCode"
    )
    public ApiResult<Void> update(@PathVariable String roleCode, @RequestBody RoleController.SaveRoleReqBody req) {
        this.validatePermModules(req);
        this.privilegeGuard.assertCanSaveRole(CurrentUser.get(), roleCode, req.getModulePerms(), req.getActionPerms(), true);
        RoleService.SaveRoleReq sr = req.toSaveRoleReq();
        this.privilegeGuard.assertScopesAndFieldsWithin(CurrentUser.get(), "角色 " + roleCode, sr.modulePerms(), sr.dataScopes(), sr.fieldPerms());
        this.roleService.update(roleCode, sr);
        return ApiResult.ok();
    }

    @DeleteMapping({"/api/roles/{roleCode}"})
    @RequirePerm(
        module = "system",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "system",
        objType = "role",
        objIdSpEL = "#roleCode"
    )
    public ApiResult<Void> delete(@PathVariable String roleCode) {
        LoginUser current = CurrentUser.get();
        if (!this.rbacService.isAdmin(current)) {
            throw BusinessException.forbidden("仅管理员可删除角色");
        } else {
            this.roleService.delete(roleCode);
            return ApiResult.ok();
        }
    }

    @GetMapping({"/api/modules"})
    @RequirePerm(
        module = "system"
    )
    public ApiResult<List<ModuleRegistry.ModuleDef>> modules() {
        return ApiResult.ok(this.moduleRegistry.list());
    }

    private void validatePermModules(RoleController.SaveRoleReqBody req) {
        this.checkModules(this.moduleRegistry, "modulePerms", req.getModulePerms());
        this.checkModules(this.moduleRegistry, "fieldPerms", req.getFieldPerms());
        this.checkModules(this.moduleRegistry, "actionPerms", req.getActionPerms());
        if (req.getDataScopes() != null) {
            req.getDataScopes().keySet().forEach(code -> {
                if (!"type".equals(code) && !"departments".equals(code)) {
                    if (!this.moduleRegistry.exists(code)) {
                        throw BusinessException.badRequest("未知的模块编码：" + code + "（dataScopes）");
                    }
                }
            });
        }
    }

    private void checkModules(ModuleRegistry registry, String label, Map<String, Map<String, Object>> perms) {
        if (perms != null) {
            perms.keySet().forEach(code -> {
                if (!registry.exists(code)) {
                    throw BusinessException.badRequest("未知的模块编码：" + code + "（" + label + "）");
                }
            });
        }
    }

    
    public RoleController(
        final RoleMapper roleMapper,
        final RoleModulePermMapper modulePermMapper,
        final RoleActionPermMapper actionPermMapper,
        final RoleDataScopeMapper dataScopeMapper,
        final RoleFieldPermMapper fieldPermMapper,
        final RoleService roleService,
        final RbacService rbacService,
        final ModuleRegistry moduleRegistry,
        final com.dcim.security.PrivilegeGuard privilegeGuard
    ) {
        this.privilegeGuard = privilegeGuard;
        this.roleMapper = roleMapper;
        this.modulePermMapper = modulePermMapper;
        this.actionPermMapper = actionPermMapper;
        this.dataScopeMapper = dataScopeMapper;
        this.fieldPermMapper = fieldPermMapper;
        this.roleService = roleService;
        this.rbacService = rbacService;
        this.moduleRegistry = moduleRegistry;
    }

    public static class SaveRoleReqBody {
        private String roleCode;
        private String name;
        private String description;
        private Map<String, Map<String, Object>> modulePerms;
        private Map<String, Object> dataScopes;
        private Map<String, Map<String, Object>> fieldPerms;
        private Map<String, Map<String, Object>> actionPerms;

        RoleService.SaveRoleReq toSaveRoleReq() {
            Map<String, Map<String, Object>> scopes = normalizeDataScopes(this.dataScopes);
            if (scopes.isEmpty() && isFlatScope(this.dataScopes) && this.modulePerms != null) {
                String type = String.valueOf(this.dataScopes.get("type"));
                this.modulePerms.forEach((mod, perms) -> {
                    boolean read = perms != null && Boolean.TRUE.equals(perms.get("read"));
                    if (read) {
                        scopes.put(mod, new LinkedHashMap<>(Map.of("type", type)));
                    }
                });
            }

            // v3.4：字段权限规整为 {hidden, readonly}，未知模块 / 字段直接 400
            Map<String, Map<String, Object>> fields = this.fieldPerms == null ? null
                : com.dcim.security.field.FieldPolicyService.normalizeRoleConfig(this.fieldPerms);
            return new RoleService.SaveRoleReq(this.name, this.description, this.modulePerms, scopes, fields, this.actionPerms);
        }

        private static boolean isFlatScope(Map<String, Object> raw) {
            return raw != null && raw.get("type") instanceof String ? raw.keySet().stream().allMatch(k -> "type".equals(k) || "departments".equals(k)) : false;
        }

        private static Map<String, Map<String, Object>> normalizeDataScopes(Map<String, Object> raw) {
            Map<String, Map<String, Object>> out = new LinkedHashMap<>();
            if (raw != null && !raw.isEmpty() && !isFlatScope(raw)) {
                raw.forEach((mod, val) -> {
                    if (val instanceof Map<?, ?> m) {
                        Map<String, Object> scope = new LinkedHashMap<>();
                        m.forEach((k, v) -> scope.put(String.valueOf(k), v));
                        out.put(mod, scope);
                    } else if (val instanceof String s) {
                        out.put(mod, new LinkedHashMap<>(Map.of("type", s)));
                    }
                });
                return out;
            } else {
                return out;
            }
        }

        
        public String getRoleCode() {
            return this.roleCode;
        }

        
        public String getName() {
            return this.name;
        }

        
        public String getDescription() {
            return this.description;
        }

        
        public Map<String, Map<String, Object>> getModulePerms() {
            return this.modulePerms;
        }

        
        public Map<String, Object> getDataScopes() {
            return this.dataScopes;
        }

        
        public Map<String, Map<String, Object>> getFieldPerms() {
            return this.fieldPerms;
        }

        
        public Map<String, Map<String, Object>> getActionPerms() {
            return this.actionPerms;
        }

        
        public void setRoleCode(final String roleCode) {
            this.roleCode = roleCode;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setDescription(final String description) {
            this.description = description;
        }

        
        public void setModulePerms(final Map<String, Map<String, Object>> modulePerms) {
            this.modulePerms = modulePerms;
        }

        
        public void setDataScopes(final Map<String, Object> dataScopes) {
            this.dataScopes = dataScopes;
        }

        
        public void setFieldPerms(final Map<String, Map<String, Object>> fieldPerms) {
            this.fieldPerms = fieldPerms;
        }

        
        public void setActionPerms(final Map<String, Map<String, Object>> actionPerms) {
            this.actionPerms = actionPerms;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof RoleController.SaveRoleReqBody other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$roleCode = this.getRoleCode();
                Object other$roleCode = other.getRoleCode();
                if (this$roleCode == null ? other$roleCode == null : this$roleCode.equals(other$roleCode)) {
                    Object this$name = this.getName();
                    Object other$name = other.getName();
                    if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                        Object this$description = this.getDescription();
                        Object other$description = other.getDescription();
                        if (this$description == null ? other$description == null : this$description.equals(other$description)) {
                            Object this$modulePerms = this.getModulePerms();
                            Object other$modulePerms = other.getModulePerms();
                            if (this$modulePerms == null ? other$modulePerms == null : this$modulePerms.equals(other$modulePerms)) {
                                Object this$dataScopes = this.getDataScopes();
                                Object other$dataScopes = other.getDataScopes();
                                if (this$dataScopes == null ? other$dataScopes == null : this$dataScopes.equals(other$dataScopes)) {
                                    Object this$fieldPerms = this.getFieldPerms();
                                    Object other$fieldPerms = other.getFieldPerms();
                                    if (this$fieldPerms == null ? other$fieldPerms == null : this$fieldPerms.equals(other$fieldPerms)) {
                                        Object this$actionPerms = this.getActionPerms();
                                        Object other$actionPerms = other.getActionPerms();
                                        return this$actionPerms == null ? other$actionPerms == null : this$actionPerms.equals(other$actionPerms);
                                    } else {
                                        return false;
                                    }
                                } else {
                                    return false;
                                }
                            } else {
                                return false;
                            }
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof RoleController.SaveRoleReqBody;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $roleCode = this.getRoleCode();
            result = result * 59 + ($roleCode == null ? 43 : $roleCode.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $description = this.getDescription();
            result = result * 59 + ($description == null ? 43 : $description.hashCode());
            Object $modulePerms = this.getModulePerms();
            result = result * 59 + ($modulePerms == null ? 43 : $modulePerms.hashCode());
            Object $dataScopes = this.getDataScopes();
            result = result * 59 + ($dataScopes == null ? 43 : $dataScopes.hashCode());
            Object $fieldPerms = this.getFieldPerms();
            result = result * 59 + ($fieldPerms == null ? 43 : $fieldPerms.hashCode());
            Object $actionPerms = this.getActionPerms();
            return result * 59 + ($actionPerms == null ? 43 : $actionPerms.hashCode());
        }

        
        @Override
        public String toString() {
            return "RoleController.SaveRoleReqBody(roleCode="
                + this.getRoleCode()
                + ", name="
                + this.getName()
                + ", description="
                + this.getDescription()
                + ", modulePerms="
                + this.getModulePerms()
                + ", dataScopes="
                + this.getDataScopes()
                + ", fieldPerms="
                + this.getFieldPerms()
                + ", actionPerms="
                + this.getActionPerms()
                + ")";
        }
    }
}
