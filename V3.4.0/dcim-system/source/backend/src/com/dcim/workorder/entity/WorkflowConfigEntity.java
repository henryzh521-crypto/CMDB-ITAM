package com.dcim.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.JsonListTypeHandler;
import java.time.LocalDateTime;
import java.util.List;

@TableName(
    value = "workflow_config",
    autoResultMap = true
)
public class WorkflowConfigEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String woType;
    private Boolean needApproval;
    @TableField(
        typeHandler = JsonListTypeHandler.class
    )
    private List<Object> approvalRoles;
    @TableField(
        typeHandler = JsonListTypeHandler.class
    )
    private List<Object> approvalUserLevels;
    private String template;
    private Boolean builtIn;
    private LocalDateTime updatedAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getWoType() {
        return this.woType;
    }

    
    public Boolean getNeedApproval() {
        return this.needApproval;
    }

    
    public List<Object> getApprovalRoles() {
        return this.approvalRoles;
    }

    
    public List<Object> getApprovalUserLevels() {
        return this.approvalUserLevels;
    }

    
    public String getTemplate() {
        return this.template;
    }

    
    public Boolean getBuiltIn() {
        return this.builtIn;
    }

    
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setWoType(final String woType) {
        this.woType = woType;
    }

    
    public void setNeedApproval(final Boolean needApproval) {
        this.needApproval = needApproval;
    }

    
    public void setApprovalRoles(final List<Object> approvalRoles) {
        this.approvalRoles = approvalRoles;
    }

    
    public void setApprovalUserLevels(final List<Object> approvalUserLevels) {
        this.approvalUserLevels = approvalUserLevels;
    }

    
    public void setTemplate(final String template) {
        this.template = template;
    }

    
    public void setBuiltIn(final Boolean builtIn) {
        this.builtIn = builtIn;
    }

    
    public void setUpdatedAt(final LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof WorkflowConfigEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$needApproval = this.getNeedApproval();
                Object other$needApproval = other.getNeedApproval();
                if (this$needApproval == null ? other$needApproval == null : this$needApproval.equals(other$needApproval)) {
                    Object this$builtIn = this.getBuiltIn();
                    Object other$builtIn = other.getBuiltIn();
                    if (this$builtIn == null ? other$builtIn == null : this$builtIn.equals(other$builtIn)) {
                        Object this$woType = this.getWoType();
                        Object other$woType = other.getWoType();
                        if (this$woType == null ? other$woType == null : this$woType.equals(other$woType)) {
                            Object this$approvalRoles = this.getApprovalRoles();
                            Object other$approvalRoles = other.getApprovalRoles();
                            if (this$approvalRoles == null ? other$approvalRoles == null : this$approvalRoles.equals(other$approvalRoles)) {
                                Object this$approvalUserLevels = this.getApprovalUserLevels();
                                Object other$approvalUserLevels = other.getApprovalUserLevels();
                                if (this$approvalUserLevels == null
                                    ? other$approvalUserLevels == null
                                    : this$approvalUserLevels.equals(other$approvalUserLevels)) {
                                    Object this$template = this.getTemplate();
                                    Object other$template = other.getTemplate();
                                    if (this$template == null ? other$template == null : this$template.equals(other$template)) {
                                        Object this$updatedAt = this.getUpdatedAt();
                                        Object other$updatedAt = other.getUpdatedAt();
                                        return this$updatedAt == null ? other$updatedAt == null : this$updatedAt.equals(other$updatedAt);
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
            } else {
                return false;
            }
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof WorkflowConfigEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $needApproval = this.getNeedApproval();
        result = result * 59 + ($needApproval == null ? 43 : $needApproval.hashCode());
        Object $builtIn = this.getBuiltIn();
        result = result * 59 + ($builtIn == null ? 43 : $builtIn.hashCode());
        Object $woType = this.getWoType();
        result = result * 59 + ($woType == null ? 43 : $woType.hashCode());
        Object $approvalRoles = this.getApprovalRoles();
        result = result * 59 + ($approvalRoles == null ? 43 : $approvalRoles.hashCode());
        Object $approvalUserLevels = this.getApprovalUserLevels();
        result = result * 59 + ($approvalUserLevels == null ? 43 : $approvalUserLevels.hashCode());
        Object $template = this.getTemplate();
        result = result * 59 + ($template == null ? 43 : $template.hashCode());
        Object $updatedAt = this.getUpdatedAt();
        return result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "WorkflowConfigEntity(id="
            + this.getId()
            + ", woType="
            + this.getWoType()
            + ", needApproval="
            + this.getNeedApproval()
            + ", approvalRoles="
            + this.getApprovalRoles()
            + ", approvalUserLevels="
            + this.getApprovalUserLevels()
            + ", template="
            + this.getTemplate()
            + ", builtIn="
            + this.getBuiltIn()
            + ", updatedAt="
            + this.getUpdatedAt()
            + ")";
    }
}
