package com.dcim.system.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.BusinessException;
import com.dcim.security.LoginUserLoader;
import com.dcim.system.entity.RoleActionPermEntity;
import com.dcim.system.entity.RoleDataScopeEntity;
import com.dcim.system.entity.RoleEntity;
import com.dcim.system.entity.RoleFieldPermEntity;
import com.dcim.system.entity.RoleModulePermEntity;
import com.dcim.system.entity.UserRoleEntity;
import com.dcim.system.mapper.RoleActionPermMapper;
import com.dcim.system.mapper.RoleDataScopeMapper;
import com.dcim.system.mapper.RoleFieldPermMapper;
import com.dcim.system.mapper.RoleMapper;
import com.dcim.system.mapper.RoleModulePermMapper;
import com.dcim.system.mapper.UserRoleMapper;
import com.dcim.workorder.mapper.WorkflowConfigMapper;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleModulePermMapper modulePermMapper;
    private final RoleActionPermMapper actionPermMapper;
    private final RoleDataScopeMapper dataScopeMapper;
    private final RoleFieldPermMapper fieldPermMapper;
    private final WorkflowConfigMapper workflowConfigMapper;
    private final LoginUserLoader loginUserLoader;

    @Transactional
    public void create(String roleCode, RoleService.SaveRoleReq req) {
        if (this.selectByCode(roleCode) != null) {
            throw BusinessException.conflict("角色编码已存在");
        } else {
            this.roleMapper.purgeTombstone(roleCode);
            RoleEntity role = new RoleEntity();
            role.setRoleCode(roleCode);
            role.setName(req.name());
            role.setDescription(req.description());
            role.setBuiltIn(false);
            this.roleMapper.insert(role);
            this.replacePerms(roleCode, req);
            this.loginUserLoader.evictAll();
        }
    }

    @Transactional
    public void update(String roleCode, RoleService.SaveRoleReq req) {
        RoleEntity role = this.selectByCode(roleCode);
        if (role == null) {
            throw BusinessException.notFound("角色不存在");
        } else {
            boolean builtIn = Boolean.TRUE.equals(role.getBuiltIn());
            if (builtIn && req.name() != null && !req.name().equals(role.getName())) {
                throw BusinessException.badRequest("内置角色不可改名");
            } else {
                if (req.name() != null) {
                    role.setName(req.name());
                }

                if (req.description() != null) {
                    role.setDescription(req.description());
                }

                this.roleMapper.updateById(role);
                this.replacePerms(roleCode, req);
                this.loginUserLoader.evictAll();
            }
        }
    }

    @Transactional
    public void delete(String roleCode) {
        RoleEntity role = this.selectByCode(roleCode);
        if (role == null) {
            throw BusinessException.notFound("角色不存在");
        } else if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw BusinessException.forbidden("内置角色不可删除");
        } else {
            Long bound = this.userRoleMapper.selectCount(Wrappers.<UserRoleEntity>lambdaQuery().eq(UserRoleEntity::getRoleCode, roleCode));
            if (bound != null && bound > 0L) {
                throw BusinessException.conflict("该角色已分配用户，无法删除");
            } else if (this.workflowConfigMapper.countApprovalRoleUsage(roleCode) > 0) {
                throw BusinessException.conflict("该角色正被工单审批链引用，请先在「工单配置-审批设置」中调整审批链后再删除");
            } else {
                this.roleMapper.deleteById(role.getId());
                this.clearPerms(roleCode);
                this.loginUserLoader.evictAll();
            }
        }
    }

    public RoleEntity selectByCode(String roleCode) {
        return (RoleEntity)this.roleMapper.selectOne(Wrappers.<RoleEntity>lambdaQuery().eq(RoleEntity::getRoleCode, roleCode));
    }

    private void replacePerms(String roleCode, RoleService.SaveRoleReq req) {
        this.clearPerms(roleCode);
        if (req.modulePerms() != null) {
            req.modulePerms().forEach((module, m) -> {
                RoleModulePermEntity e = new RoleModulePermEntity();
                e.setRoleCode(roleCode);
                e.setModule(module);
                e.setCanRead(Boolean.TRUE.equals(m.get("read")));
                e.setCanWrite(Boolean.TRUE.equals(m.get("write")));
                this.modulePermMapper.insert(e);
            });
        }

        if (req.dataScopes() != null) {
            req.dataScopes().forEach((module, scope) -> {
                if (scope != null) {
                    RoleDataScopeEntity e = new RoleDataScopeEntity();
                    e.setRoleCode(roleCode);
                    e.setModule(module);
                    e.setScope((Map<String, Object>)scope);
                    this.dataScopeMapper.insert(e);
                }
            });
        }

        if (req.fieldPerms() != null) {
            req.fieldPerms().forEach((module, fields) -> {
                RoleFieldPermEntity e = new RoleFieldPermEntity();
                e.setRoleCode(roleCode);
                e.setModule(module);
                e.setFields((Map<String, Object>)fields);
                this.fieldPermMapper.insert(e);
            });
        }

        if (req.actionPerms() != null) {
            req.actionPerms().forEach((module, actions) -> {
                RoleActionPermEntity e = new RoleActionPermEntity();
                e.setRoleCode(roleCode);
                e.setModule(module);
                e.setActions((Map<String, Object>)actions);
                this.actionPermMapper.insert(e);
            });
        }
    }

    private void clearPerms(String roleCode) {
        this.modulePermMapper.delete(Wrappers.<RoleModulePermEntity>lambdaQuery().eq(RoleModulePermEntity::getRoleCode, roleCode));
        this.actionPermMapper.delete(Wrappers.<RoleActionPermEntity>lambdaQuery().eq(RoleActionPermEntity::getRoleCode, roleCode));
        this.dataScopeMapper.delete(Wrappers.<RoleDataScopeEntity>lambdaQuery().eq(RoleDataScopeEntity::getRoleCode, roleCode));
        this.fieldPermMapper.delete(Wrappers.<RoleFieldPermEntity>lambdaQuery().eq(RoleFieldPermEntity::getRoleCode, roleCode));
    }

    
    public RoleService(
        final RoleMapper roleMapper,
        final UserRoleMapper userRoleMapper,
        final RoleModulePermMapper modulePermMapper,
        final RoleActionPermMapper actionPermMapper,
        final RoleDataScopeMapper dataScopeMapper,
        final RoleFieldPermMapper fieldPermMapper,
        final WorkflowConfigMapper workflowConfigMapper,
        final LoginUserLoader loginUserLoader
    ) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.modulePermMapper = modulePermMapper;
        this.actionPermMapper = actionPermMapper;
        this.dataScopeMapper = dataScopeMapper;
        this.fieldPermMapper = fieldPermMapper;
        this.workflowConfigMapper = workflowConfigMapper;
        this.loginUserLoader = loginUserLoader;
    }

    public static record SaveRoleReq(
        String name,
        String description,
        Map<String, Map<String, Object>> modulePerms,
        Map<String, Map<String, Object>> dataScopes,
        Map<String, Map<String, Object>> fieldPerms,
        Map<String, Map<String, Object>> actionPerms
    ) {
    }
}
