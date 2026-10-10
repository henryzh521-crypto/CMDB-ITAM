package com.dcim.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.JsonbTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(
    value = "audit_log",
    autoResultMap = true
)
public class AuditLogEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String actor;
    private String action;
    private String module;
    private String objType;
    private String objId;
    @TableField(
        typeHandler = JsonbTypeHandler.class
    )
    private Map<String, Object> detail;
    private String ip;
    private LocalDateTime createdAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getActor() {
        return this.actor;
    }

    
    public String getAction() {
        return this.action;
    }

    
    public String getModule() {
        return this.module;
    }

    
    public String getObjType() {
        return this.objType;
    }

    
    public String getObjId() {
        return this.objId;
    }

    
    public Map<String, Object> getDetail() {
        return this.detail;
    }

    
    public String getIp() {
        return this.ip;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setActor(final String actor) {
        this.actor = actor;
    }

    
    public void setAction(final String action) {
        this.action = action;
    }

    
    public void setModule(final String module) {
        this.module = module;
    }

    
    public void setObjType(final String objType) {
        this.objType = objType;
    }

    
    public void setObjId(final String objId) {
        this.objId = objId;
    }

    
    public void setDetail(final Map<String, Object> detail) {
        this.detail = detail;
    }

    
    public void setIp(final String ip) {
        this.ip = ip;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof AuditLogEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$actor = this.getActor();
                Object other$actor = other.getActor();
                if (this$actor == null ? other$actor == null : this$actor.equals(other$actor)) {
                    Object this$action = this.getAction();
                    Object other$action = other.getAction();
                    if (this$action == null ? other$action == null : this$action.equals(other$action)) {
                        Object this$module = this.getModule();
                        Object other$module = other.getModule();
                        if (this$module == null ? other$module == null : this$module.equals(other$module)) {
                            Object this$objType = this.getObjType();
                            Object other$objType = other.getObjType();
                            if (this$objType == null ? other$objType == null : this$objType.equals(other$objType)) {
                                Object this$objId = this.getObjId();
                                Object other$objId = other.getObjId();
                                if (this$objId == null ? other$objId == null : this$objId.equals(other$objId)) {
                                    Object this$detail = this.getDetail();
                                    Object other$detail = other.getDetail();
                                    if (this$detail == null ? other$detail == null : this$detail.equals(other$detail)) {
                                        Object this$ip = this.getIp();
                                        Object other$ip = other.getIp();
                                        if (this$ip == null ? other$ip == null : this$ip.equals(other$ip)) {
                                            Object this$createdAt = this.getCreatedAt();
                                            Object other$createdAt = other.getCreatedAt();
                                            return this$createdAt == null ? other$createdAt == null : this$createdAt.equals(other$createdAt);
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
            } else {
                return false;
            }
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof AuditLogEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $actor = this.getActor();
        result = result * 59 + ($actor == null ? 43 : $actor.hashCode());
        Object $action = this.getAction();
        result = result * 59 + ($action == null ? 43 : $action.hashCode());
        Object $module = this.getModule();
        result = result * 59 + ($module == null ? 43 : $module.hashCode());
        Object $objType = this.getObjType();
        result = result * 59 + ($objType == null ? 43 : $objType.hashCode());
        Object $objId = this.getObjId();
        result = result * 59 + ($objId == null ? 43 : $objId.hashCode());
        Object $detail = this.getDetail();
        result = result * 59 + ($detail == null ? 43 : $detail.hashCode());
        Object $ip = this.getIp();
        result = result * 59 + ($ip == null ? 43 : $ip.hashCode());
        Object $createdAt = this.getCreatedAt();
        return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "AuditLogEntity(id="
            + this.getId()
            + ", actor="
            + this.getActor()
            + ", action="
            + this.getAction()
            + ", module="
            + this.getModule()
            + ", objType="
            + this.getObjType()
            + ", objId="
            + this.getObjId()
            + ", detail="
            + this.getDetail()
            + ", ip="
            + this.getIp()
            + ", createdAt="
            + this.getCreatedAt()
            + ")";
    }
}
