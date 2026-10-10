package com.dcim.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_role")
public class UserRoleEntity {
    @TableId(
        value = "id",
        type = IdType.AUTO
    )
    private Long id;
    private Long userId;
    private String roleCode;

    
    public Long getId() {
        return this.id;
    }

    
    public Long getUserId() {
        return this.userId;
    }

    
    public String getRoleCode() {
        return this.roleCode;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setUserId(final Long userId) {
        this.userId = userId;
    }

    
    public void setRoleCode(final String roleCode) {
        this.roleCode = roleCode;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof UserRoleEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$userId = this.getUserId();
                Object other$userId = other.getUserId();
                if (this$userId == null ? other$userId == null : this$userId.equals(other$userId)) {
                    Object this$roleCode = this.getRoleCode();
                    Object other$roleCode = other.getRoleCode();
                    return this$roleCode == null ? other$roleCode == null : this$roleCode.equals(other$roleCode);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof UserRoleEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $userId = this.getUserId();
        result = result * 59 + ($userId == null ? 43 : $userId.hashCode());
        Object $roleCode = this.getRoleCode();
        return result * 59 + ($roleCode == null ? 43 : $roleCode.hashCode());
    }

    
    @Override
    public String toString() {
        return "UserRoleEntity(id=" + this.getId() + ", userId=" + this.getUserId() + ", roleCode=" + this.getRoleCode() + ")";
    }
}
