package com.dcim.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.JsonbTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(
    value = "asset_event",
    autoResultMap = true
)
public class AssetEventEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String assetNo;
    private String eventType;
    private String operator;
    private String refDoc;
    @TableField(
        typeHandler = JsonbTypeHandler.class
    )
    private Map<String, Object> snapshot;
    private LocalDateTime eventTime;

    
    public Long getId() {
        return this.id;
    }

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public String getEventType() {
        return this.eventType;
    }

    
    public String getOperator() {
        return this.operator;
    }

    
    public String getRefDoc() {
        return this.refDoc;
    }

    
    public Map<String, Object> getSnapshot() {
        return this.snapshot;
    }

    
    public LocalDateTime getEventTime() {
        return this.eventTime;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
    }

    
    public void setEventType(final String eventType) {
        this.eventType = eventType;
    }

    
    public void setOperator(final String operator) {
        this.operator = operator;
    }

    
    public void setRefDoc(final String refDoc) {
        this.refDoc = refDoc;
    }

    
    public void setSnapshot(final Map<String, Object> snapshot) {
        this.snapshot = snapshot;
    }

    
    public void setEventTime(final LocalDateTime eventTime) {
        this.eventTime = eventTime;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof AssetEventEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$assetNo = this.getAssetNo();
                Object other$assetNo = other.getAssetNo();
                if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                    Object this$eventType = this.getEventType();
                    Object other$eventType = other.getEventType();
                    if (this$eventType == null ? other$eventType == null : this$eventType.equals(other$eventType)) {
                        Object this$operator = this.getOperator();
                        Object other$operator = other.getOperator();
                        if (this$operator == null ? other$operator == null : this$operator.equals(other$operator)) {
                            Object this$refDoc = this.getRefDoc();
                            Object other$refDoc = other.getRefDoc();
                            if (this$refDoc == null ? other$refDoc == null : this$refDoc.equals(other$refDoc)) {
                                Object this$snapshot = this.getSnapshot();
                                Object other$snapshot = other.getSnapshot();
                                if (this$snapshot == null ? other$snapshot == null : this$snapshot.equals(other$snapshot)) {
                                    Object this$eventTime = this.getEventTime();
                                    Object other$eventTime = other.getEventTime();
                                    return this$eventTime == null ? other$eventTime == null : this$eventTime.equals(other$eventTime);
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
        return other instanceof AssetEventEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $eventType = this.getEventType();
        result = result * 59 + ($eventType == null ? 43 : $eventType.hashCode());
        Object $operator = this.getOperator();
        result = result * 59 + ($operator == null ? 43 : $operator.hashCode());
        Object $refDoc = this.getRefDoc();
        result = result * 59 + ($refDoc == null ? 43 : $refDoc.hashCode());
        Object $snapshot = this.getSnapshot();
        result = result * 59 + ($snapshot == null ? 43 : $snapshot.hashCode());
        Object $eventTime = this.getEventTime();
        return result * 59 + ($eventTime == null ? 43 : $eventTime.hashCode());
    }

    
    @Override
    public String toString() {
        return "AssetEventEntity(id="
            + this.getId()
            + ", assetNo="
            + this.getAssetNo()
            + ", eventType="
            + this.getEventType()
            + ", operator="
            + this.getOperator()
            + ", refDoc="
            + this.getRefDoc()
            + ", snapshot="
            + this.getSnapshot()
            + ", eventTime="
            + this.getEventTime()
            + ")";
    }
}
