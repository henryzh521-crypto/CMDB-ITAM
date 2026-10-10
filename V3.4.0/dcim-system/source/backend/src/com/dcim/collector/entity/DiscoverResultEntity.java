package com.dcim.collector.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.JsonbTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(
    value = "discover_result",
    autoResultMap = true
)
public class DiscoverResultEntity {
    public static final String ST_PENDING_CLAIM = "待认领";
    public static final String ST_MATCHED = "已匹配";
    public static final String ST_CLAIMED = "已认领";
    public static final String ST_IGNORED = "已忽略";
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private Long taskId;
    private String bmcIp;
    private String sn;
    private String manufacturer;
    private String model;
    @TableField(
        typeHandler = JsonbTypeHandler.class
    )
    private Map<String, Object> raw;
    private Long endpointId;
    private String assetNo;
    private String status;
    @TableField(
        typeHandler = JsonbTypeHandler.class
    )
    private Map<String, Object> decision;
    private LocalDateTime claimedAt;
    private LocalDateTime createdAt;

    
    public Long getId() {
        return this.id;
    }

    
    public Long getTaskId() {
        return this.taskId;
    }

    
    public String getBmcIp() {
        return this.bmcIp;
    }

    
    public String getSn() {
        return this.sn;
    }

    
    public String getManufacturer() {
        return this.manufacturer;
    }

    
    public String getModel() {
        return this.model;
    }

    
    public Map<String, Object> getRaw() {
        return this.raw;
    }

    
    public Long getEndpointId() {
        return this.endpointId;
    }

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public String getStatus() {
        return this.status;
    }

    
    public Map<String, Object> getDecision() {
        return this.decision;
    }

    
    public LocalDateTime getClaimedAt() {
        return this.claimedAt;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setTaskId(final Long taskId) {
        this.taskId = taskId;
    }

    
    public void setBmcIp(final String bmcIp) {
        this.bmcIp = bmcIp;
    }

    
    public void setSn(final String sn) {
        this.sn = sn;
    }

    
    public void setManufacturer(final String manufacturer) {
        this.manufacturer = manufacturer;
    }

    
    public void setModel(final String model) {
        this.model = model;
    }

    
    public void setRaw(final Map<String, Object> raw) {
        this.raw = raw;
    }

    
    public void setEndpointId(final Long endpointId) {
        this.endpointId = endpointId;
    }

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
    }

    
    public void setStatus(final String status) {
        this.status = status;
    }

    
    public void setDecision(final Map<String, Object> decision) {
        this.decision = decision;
    }

    
    public void setClaimedAt(final LocalDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof DiscoverResultEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$taskId = this.getTaskId();
                Object other$taskId = other.getTaskId();
                if (this$taskId == null ? other$taskId == null : this$taskId.equals(other$taskId)) {
                    Object this$endpointId = this.getEndpointId();
                    Object other$endpointId = other.getEndpointId();
                    if (this$endpointId == null ? other$endpointId == null : this$endpointId.equals(other$endpointId)) {
                        Object this$bmcIp = this.getBmcIp();
                        Object other$bmcIp = other.getBmcIp();
                        if (this$bmcIp == null ? other$bmcIp == null : this$bmcIp.equals(other$bmcIp)) {
                            Object this$sn = this.getSn();
                            Object other$sn = other.getSn();
                            if (this$sn == null ? other$sn == null : this$sn.equals(other$sn)) {
                                Object this$manufacturer = this.getManufacturer();
                                Object other$manufacturer = other.getManufacturer();
                                if (this$manufacturer == null ? other$manufacturer == null : this$manufacturer.equals(other$manufacturer)) {
                                    Object this$model = this.getModel();
                                    Object other$model = other.getModel();
                                    if (this$model == null ? other$model == null : this$model.equals(other$model)) {
                                        Object this$raw = this.getRaw();
                                        Object other$raw = other.getRaw();
                                        if (this$raw == null ? other$raw == null : this$raw.equals(other$raw)) {
                                            Object this$assetNo = this.getAssetNo();
                                            Object other$assetNo = other.getAssetNo();
                                            if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                                                Object this$status = this.getStatus();
                                                Object other$status = other.getStatus();
                                                if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                                                    Object this$decision = this.getDecision();
                                                    Object other$decision = other.getDecision();
                                                    if (this$decision == null ? other$decision == null : this$decision.equals(other$decision)) {
                                                        Object this$claimedAt = this.getClaimedAt();
                                                        Object other$claimedAt = other.getClaimedAt();
                                                        if (this$claimedAt == null ? other$claimedAt == null : this$claimedAt.equals(other$claimedAt)) {
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
        return other instanceof DiscoverResultEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $taskId = this.getTaskId();
        result = result * 59 + ($taskId == null ? 43 : $taskId.hashCode());
        Object $endpointId = this.getEndpointId();
        result = result * 59 + ($endpointId == null ? 43 : $endpointId.hashCode());
        Object $bmcIp = this.getBmcIp();
        result = result * 59 + ($bmcIp == null ? 43 : $bmcIp.hashCode());
        Object $sn = this.getSn();
        result = result * 59 + ($sn == null ? 43 : $sn.hashCode());
        Object $manufacturer = this.getManufacturer();
        result = result * 59 + ($manufacturer == null ? 43 : $manufacturer.hashCode());
        Object $model = this.getModel();
        result = result * 59 + ($model == null ? 43 : $model.hashCode());
        Object $raw = this.getRaw();
        result = result * 59 + ($raw == null ? 43 : $raw.hashCode());
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $decision = this.getDecision();
        result = result * 59 + ($decision == null ? 43 : $decision.hashCode());
        Object $claimedAt = this.getClaimedAt();
        result = result * 59 + ($claimedAt == null ? 43 : $claimedAt.hashCode());
        Object $createdAt = this.getCreatedAt();
        return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "DiscoverResultEntity(id="
            + this.getId()
            + ", taskId="
            + this.getTaskId()
            + ", bmcIp="
            + this.getBmcIp()
            + ", sn="
            + this.getSn()
            + ", manufacturer="
            + this.getManufacturer()
            + ", model="
            + this.getModel()
            + ", raw="
            + this.getRaw()
            + ", endpointId="
            + this.getEndpointId()
            + ", assetNo="
            + this.getAssetNo()
            + ", status="
            + this.getStatus()
            + ", decision="
            + this.getDecision()
            + ", claimedAt="
            + this.getClaimedAt()
            + ", createdAt="
            + this.getCreatedAt()
            + ")";
    }
}
