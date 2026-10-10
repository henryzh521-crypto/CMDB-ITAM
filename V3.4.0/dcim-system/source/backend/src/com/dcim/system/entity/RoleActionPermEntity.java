package com.dcim.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.JsonbTypeHandler;
import java.util.Map;

@TableName(
    value = "role_action_perm",
    autoResultMap = true
)
public class RoleActionPermEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String roleCode;
    private String module;
    @TableField(
        typeHandler = JsonbTypeHandler.class
    )
    private Map<String, Object> actions;

    
    public Long getId() {
        return this.id;
    }

    
    public String getRoleCode() {
        return this.roleCode;
    }

    
    public String getModule() {
        return this.module;
    }

    
    public Map<String, Object> getActions() {
        return this.actions;
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

    
    public void setActions(final Map<String, Object> actions) {
        this.actions = actions;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof RoleActionPermEntity other)) {
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
                        Object this$actions = this.getActions();
                        Object other$actions = other.getActions();
                        return this$actions == null ? other$actions == null : this$actions.equals(other$actions);
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
        return other instanceof RoleActionPermEntity;
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
        Object $actions = this.getActions();
        return result * 59 + ($actions == null ? 43 : $actions.hashCode());
    }

    
    @Override
    public String toString() {
        return "RoleActionPermEntity(id="
            + this.getId()
            + ", roleCode="
            + this.getRoleCode()
            + ", module="
            + this.getModule()
            + ", actions="
            + this.getActions()
            + ")";
    }
}
