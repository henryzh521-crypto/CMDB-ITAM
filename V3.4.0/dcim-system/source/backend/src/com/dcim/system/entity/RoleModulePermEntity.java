package com.dcim.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("role_module_perm")
public class RoleModulePermEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String roleCode;
    private String module;
    private Boolean canRead;
    private Boolean canWrite;

    
    public Long getId() {
        return this.id;
    }

    
    public String getRoleCode() {
        return this.roleCode;
    }

    
    public String getModule() {
        return this.module;
    }

    
    public Boolean getCanRead() {
        return this.canRead;
    }

    
    public Boolean getCanWrite() {
        return this.canWrite;
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

    
    public void setCanRead(final Boolean canRead) {
        this.canRead = canRead;
    }

    
    public void setCanWrite(final Boolean canWrite) {
        this.canWrite = canWrite;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof RoleModulePermEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$canRead = this.getCanRead();
                Object other$canRead = other.getCanRead();
                if (this$canRead == null ? other$canRead == null : this$canRead.equals(other$canRead)) {
                    Object this$canWrite = this.getCanWrite();
                    Object other$canWrite = other.getCanWrite();
                    if (this$canWrite == null ? other$canWrite == null : this$canWrite.equals(other$canWrite)) {
                        Object this$roleCode = this.getRoleCode();
                        Object other$roleCode = other.getRoleCode();
                        if (this$roleCode == null ? other$roleCode == null : this$roleCode.equals(other$roleCode)) {
                            Object this$module = this.getModule();
                            Object other$module = other.getModule();
                            return this$module == null ? other$module == null : this$module.equals(other$module);
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
        return other instanceof RoleModulePermEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $canRead = this.getCanRead();
        result = result * 59 + ($canRead == null ? 43 : $canRead.hashCode());
        Object $canWrite = this.getCanWrite();
        result = result * 59 + ($canWrite == null ? 43 : $canWrite.hashCode());
        Object $roleCode = this.getRoleCode();
        result = result * 59 + ($roleCode == null ? 43 : $roleCode.hashCode());
        Object $module = this.getModule();
        return result * 59 + ($module == null ? 43 : $module.hashCode());
    }

    
    @Override
    public String toString() {
        return "RoleModulePermEntity(id="
            + this.getId()
            + ", roleCode="
            + this.getRoleCode()
            + ", module="
            + this.getModule()
            + ", canRead="
            + this.getCanRead()
            + ", canWrite="
            + this.getCanWrite()
            + ")";
    }
}
