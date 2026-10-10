package com.dcim.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.JsonbTypeHandler;
import java.util.Map;

@TableName(
    value = "role_data_scope",
    autoResultMap = true
)
public class RoleDataScopeEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String roleCode;
    private String module;
    @TableField(
        typeHandler = JsonbTypeHandler.class
    )
    private Map<String, Object> scope;

    
    public Long getId() {
        return this.id;
    }

    
    public String getRoleCode() {
        return this.roleCode;
    }

    
    public String getModule() {
        return this.module;
    }

    
    public Map<String, Object> getScope() {
        return this.scope;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setRoleCode(final String roleCode) {
        this.roleCode = roleCode;
    }

    
    public void setModule(final String module) {
        this.module = module;
    }

    
    public void setScope(final Map<String, Object> scope) {
        this.scope = scope;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof RoleDataScopeEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$roleCode = this.getRoleCode();
                Object other$roleCode = other.getRoleCode();
                if (this$roleCode == null ? other$roleCode == null : this$roleCode.equals(other$roleCode)) {
                    Object this$module = this.getModule();
                    Object other$module = other.getModule();
                    if (this$module == null ? other$module == null : this$module.equals(other$module)) {
                        Object this$scope = this.getScope();
                        Object other$scope = other.getScope();
                        return this$scope == null ? other$scope == null : this$scope.equals(other$scope);
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
        return other instanceof RoleDataScopeEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $roleCode = this.getRoleCode();
        result = result * 59 + ($roleCode == null ? 43 : $roleCode.hashCode());
        Object $module = this.getModule();
        result = result * 59 + ($module == null ? 43 : $module.hashCode());
        Object $scope = this.getScope();
        return result * 59 + ($scope == null ? 43 : $scope.hashCode());
    }

    
    @Override
    public String toString() {
        return "RoleDataScopeEntity(id="
            + this.getId()
            + ", roleCode="
            + this.getRoleCode()
            + ", module="
            + this.getModule()
            + ", scope="
            + this.getScope()
            + ")";
    }
}
