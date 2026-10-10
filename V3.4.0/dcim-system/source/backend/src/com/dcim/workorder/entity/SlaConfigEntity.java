package com.dcim.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sla_config")
public class SlaConfigEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String woType;
    private String priority;
    private Integer responseMinutes;
    private Integer resolveMinutes;
    private String escalateTo;

    
    public Long getId() {
        return this.id;
    }

    
    public String getWoType() {
        return this.woType;
    }

    
    public String getPriority() {
        return this.priority;
    }

    
    public Integer getResponseMinutes() {
        return this.responseMinutes;
    }

    
    public Integer getResolveMinutes() {
        return this.resolveMinutes;
    }

    
    public String getEscalateTo() {
        return this.escalateTo;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setWoType(final String woType) {
        this.woType = woType;
    }

    
    public void setPriority(final String priority) {
        this.priority = priority;
    }

    
    public void setResponseMinutes(final Integer responseMinutes) {
        this.responseMinutes = responseMinutes;
    }

    
    public void setResolveMinutes(final Integer resolveMinutes) {
        this.resolveMinutes = resolveMinutes;
    }

    
    public void setEscalateTo(final String escalateTo) {
        this.escalateTo = escalateTo;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof SlaConfigEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$responseMinutes = this.getResponseMinutes();
                Object other$responseMinutes = other.getResponseMinutes();
                if (this$responseMinutes == null ? other$responseMinutes == null : this$responseMinutes.equals(other$responseMinutes)) {
                    Object this$resolveMinutes = this.getResolveMinutes();
                    Object other$resolveMinutes = other.getResolveMinutes();
                    if (this$resolveMinutes == null ? other$resolveMinutes == null : this$resolveMinutes.equals(other$resolveMinutes)) {
                        Object this$woType = this.getWoType();
                        Object other$woType = other.getWoType();
                        if (this$woType == null ? other$woType == null : this$woType.equals(other$woType)) {
                            Object this$priority = this.getPriority();
                            Object other$priority = other.getPriority();
                            if (this$priority == null ? other$priority == null : this$priority.equals(other$priority)) {
                                Object this$escalateTo = this.getEscalateTo();
                                Object other$escalateTo = other.getEscalateTo();
                                return this$escalateTo == null ? other$escalateTo == null : this$escalateTo.equals(other$escalateTo);
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
        return other instanceof SlaConfigEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $responseMinutes = this.getResponseMinutes();
        result = result * 59 + ($responseMinutes == null ? 43 : $responseMinutes.hashCode());
        Object $resolveMinutes = this.getResolveMinutes();
        result = result * 59 + ($resolveMinutes == null ? 43 : $resolveMinutes.hashCode());
        Object $woType = this.getWoType();
        result = result * 59 + ($woType == null ? 43 : $woType.hashCode());
        Object $priority = this.getPriority();
        result = result * 59 + ($priority == null ? 43 : $priority.hashCode());
        Object $escalateTo = this.getEscalateTo();
        return result * 59 + ($escalateTo == null ? 43 : $escalateTo.hashCode());
    }

    
    @Override
    public String toString() {
        return "SlaConfigEntity(id="
            + this.getId()
            + ", woType="
            + this.getWoType()
            + ", priority="
            + this.getPriority()
            + ", responseMinutes="
            + this.getResponseMinutes()
            + ", resolveMinutes="
            + this.getResolveMinutes()
            + ", escalateTo="
            + this.getEscalateTo()
            + ")";
    }
}
