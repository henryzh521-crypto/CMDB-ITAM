package com.dcim.collector.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.JsonbTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(
    value = "hw_snapshot",
    autoResultMap = true
)
public class HwSnapshotEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private Long endpointId;
    private String assetNo;
    private String protocol;
    @TableField(
        typeHandler = JsonbTypeHandler.class
    )
    private Map<String, Object> snapshot;
    private Boolean ok;
    private String error;
    private LocalDateTime collectedAt;

    
    public Long getId() {
        return this.id;
    }

    
    public Long getEndpointId() {
        return this.endpointId;
    }

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public String getProtocol() {
        return this.protocol;
    }

    
    public Map<String, Object> getSnapshot() {
        return this.snapshot;
    }

    
    public Boolean getOk() {
        return this.ok;
    }

    
    public String getError() {
        return this.error;
    }

    
    public LocalDateTime getCollectedAt() {
        return this.collectedAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setEndpointId(final Long endpointId) {
        this.endpointId = endpointId;
    }

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
    }

    
    public void setProtocol(final String protocol) {
        this.protocol = protocol;
    }

    
    public void setSnapshot(final Map<String, Object> snapshot) {
        this.snapshot = snapshot;
    }

    
    public void setOk(final Boolean ok) {
        this.ok = ok;
    }

    
    public void setError(final String error) {
        this.error = error;
    }

    
    public void setCollectedAt(final LocalDateTime collectedAt) {
        this.collectedAt = collectedAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof HwSnapshotEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$endpointId = this.getEndpointId();
                Object other$endpointId = other.getEndpointId();
                if (this$endpointId == null ? other$endpointId == null : this$endpointId.equals(other$endpointId)) {
                    Object this$ok = this.getOk();
                    Object other$ok = other.getOk();
                    if (this$ok == null ? other$ok == null : this$ok.equals(other$ok)) {
                        Object this$assetNo = this.getAssetNo();
                        Object other$assetNo = other.getAssetNo();
                        if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                            Object this$protocol = this.getProtocol();
                            Object other$protocol = other.getProtocol();
                            if (this$protocol == null ? other$protocol == null : this$protocol.equals(other$protocol)) {
                                Object this$snapshot = this.getSnapshot();
                                Object other$snapshot = other.getSnapshot();
                                if (this$snapshot == null ? other$snapshot == null : this$snapshot.equals(other$snapshot)) {
                                    Object this$error = this.getError();
                                    Object other$error = other.getError();
                                    if (this$error == null ? other$error == null : this$error.equals(other$error)) {
                                        Object this$collectedAt = this.getCollectedAt();
                                        Object other$collectedAt = other.getCollectedAt();
                                        return this$collectedAt == null ? other$collectedAt == null : this$collectedAt.equals(other$collectedAt);
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
        return other instanceof HwSnapshotEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $endpointId = this.getEndpointId();
        result = result * 59 + ($endpointId == null ? 43 : $endpointId.hashCode());
        Object $ok = this.getOk();
        result = result * 59 + ($ok == null ? 43 : $ok.hashCode());
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $protocol = this.getProtocol();
        result = result * 59 + ($protocol == null ? 43 : $protocol.hashCode());
        Object $snapshot = this.getSnapshot();
        result = result * 59 + ($snapshot == null ? 43 : $snapshot.hashCode());
        Object $error = this.getError();
        result = result * 59 + ($error == null ? 43 : $error.hashCode());
        Object $collectedAt = this.getCollectedAt();
        return result * 59 + ($collectedAt == null ? 43 : $collectedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "HwSnapshotEntity(id="
            + this.getId()
            + ", endpointId="
            + this.getEndpointId()
            + ", assetNo="
            + this.getAssetNo()
            + ", protocol="
            + this.getProtocol()
            + ", snapshot="
            + this.getSnapshot()
            + ", ok="
            + this.getOk()
            + ", error="
            + this.getError()
            + ", collectedAt="
            + this.getCollectedAt()
            + ")";
    }
}
