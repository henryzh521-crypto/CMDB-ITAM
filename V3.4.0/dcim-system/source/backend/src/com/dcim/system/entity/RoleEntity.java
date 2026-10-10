package com.dcim.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("roles")
public class RoleEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String roleCode;
    private String name;
    private String description;
    private Boolean builtIn;
    private LocalDateTime createdAt;
    @TableLogic
    private LocalDateTime deletedAt;

    
    public Long getId() {
        return this.id;
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

    
    public Boolean getBuiltIn() {
        return this.builtIn;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public LocalDateTime getDeletedAt() {
        return this.deletedAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
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

    
    public void setBuiltIn(final Boolean builtIn) {
        this.builtIn = builtIn;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    public void setDeletedAt(final LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof RoleEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$builtIn = this.getBuiltIn();
                Object other$builtIn = other.getBuiltIn();
                if (this$builtIn == null ? other$builtIn == null : this$builtIn.equals(other$builtIn)) {
                    Object this$roleCode = this.getRoleCode();
                    Object other$roleCode = other.getRoleCode();
                    if (this$roleCode == null ? other$roleCode == null : this$roleCode.equals(other$roleCode)) {
                        Object this$name = this.getName();
                        Object other$name = other.getName();
                        if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                            Object this$description = this.getDescription();
                            Object other$description = other.getDescription();
                            if (this$description == null ? other$description == null : this$description.equals(other$description)) {
                                Object this$createdAt = this.getCreatedAt();
                                Object other$createdAt = other.getCreatedAt();
                                if (this$createdAt == null ? other$createdAt == null : this$createdAt.equals(other$createdAt)) {
                                    Object this$deletedAt = this.getDeletedAt();
                                    Object other$deletedAt = other.getDeletedAt();
                                    return this$deletedAt == null ? other$deletedAt == null : this$deletedAt.equals(other$deletedAt);
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
        return other instanceof RoleEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $builtIn = this.getBuiltIn();
        result = result * 59 + ($builtIn == null ? 43 : $builtIn.hashCode());
        Object $roleCode = this.getRoleCode();
        result = result * 59 + ($roleCode == null ? 43 : $roleCode.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        Object $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
        Object $deletedAt = this.getDeletedAt();
        return result * 59 + ($deletedAt == null ? 43 : $deletedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "RoleEntity(id="
            + this.getId()
            + ", roleCode="
            + this.getRoleCode()
            + ", name="
            + this.getName()
            + ", description="
            + this.getDescription()
            + ", builtIn="
            + this.getBuiltIn()
            + ", createdAt="
            + this.getCreatedAt()
            + ", deletedAt="
            + this.getDeletedAt()
            + ")";
    }
}
