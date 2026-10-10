package com.dcim.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("warehouse_layer")
public class WarehouseLayerEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String layerCode;
    private String shelfCode;
    private Integer layerNo;
    private String name;
    private String capacityDesc;
    private String status;
    private String remark;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getLayerCode() {
        return this.layerCode;
    }

    
    public String getShelfCode() {
        return this.shelfCode;
    }

    
    public Integer getLayerNo() {
        return this.layerNo;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getCapacityDesc() {
        return this.capacityDesc;
    }

    
    public String getStatus() {
        return this.status;
    }

    
    public String getRemark() {
        return this.remark;
    }

    
    public Integer getVersion() {
        return this.version;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setLayerCode(final String layerCode) {
        this.layerCode = layerCode;
    }

    
    public void setShelfCode(final String shelfCode) {
        this.shelfCode = shelfCode;
    }

    
    public void setLayerNo(final Integer layerNo) {
        this.layerNo = layerNo;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setCapacityDesc(final String capacityDesc) {
        this.capacityDesc = capacityDesc;
    }

    
    public void setStatus(final String status) {
        this.status = status;
    }

    
    public void setRemark(final String remark) {
        this.remark = remark;
    }

    
    public void setVersion(final Integer version) {
        this.version = version;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    public void setUpdatedAt(final LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof WarehouseLayerEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$layerNo = this.getLayerNo();
                Object other$layerNo = other.getLayerNo();
                if (this$layerNo == null ? other$layerNo == null : this$layerNo.equals(other$layerNo)) {
                    Object this$version = this.getVersion();
                    Object other$version = other.getVersion();
                    if (this$version == null ? other$version == null : this$version.equals(other$version)) {
                        Object this$layerCode = this.getLayerCode();
                        Object other$layerCode = other.getLayerCode();
                        if (this$layerCode == null ? other$layerCode == null : this$layerCode.equals(other$layerCode)) {
                            Object this$shelfCode = this.getShelfCode();
                            Object other$shelfCode = other.getShelfCode();
                            if (this$shelfCode == null ? other$shelfCode == null : this$shelfCode.equals(other$shelfCode)) {
                                Object this$name = this.getName();
                                Object other$name = other.getName();
                                if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                                    Object this$capacityDesc = this.getCapacityDesc();
                                    Object other$capacityDesc = other.getCapacityDesc();
                                    if (this$capacityDesc == null ? other$capacityDesc == null : this$capacityDesc.equals(other$capacityDesc)) {
                                        Object this$status = this.getStatus();
                                        Object other$status = other.getStatus();
                                        if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                                            Object this$remark = this.getRemark();
                                            Object other$remark = other.getRemark();
                                            if (this$remark == null ? other$remark == null : this$remark.equals(other$remark)) {
                                                Object this$createdAt = this.getCreatedAt();
                                                Object other$createdAt = other.getCreatedAt();
                                                if (this$createdAt == null ? other$createdAt == null : this$createdAt.equals(other$createdAt)) {
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
        return other instanceof WarehouseLayerEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $layerNo = this.getLayerNo();
        result = result * 59 + ($layerNo == null ? 43 : $layerNo.hashCode());
        Object $version = this.getVersion();
        result = result * 59 + ($version == null ? 43 : $version.hashCode());
        Object $layerCode = this.getLayerCode();
        result = result * 59 + ($layerCode == null ? 43 : $layerCode.hashCode());
        Object $shelfCode = this.getShelfCode();
        result = result * 59 + ($shelfCode == null ? 43 : $shelfCode.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $capacityDesc = this.getCapacityDesc();
        result = result * 59 + ($capacityDesc == null ? 43 : $capacityDesc.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $remark = this.getRemark();
        result = result * 59 + ($remark == null ? 43 : $remark.hashCode());
        Object $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
        Object $updatedAt = this.getUpdatedAt();
        return result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "WarehouseLayerEntity(id="
            + this.getId()
            + ", layerCode="
            + this.getLayerCode()
            + ", shelfCode="
            + this.getShelfCode()
            + ", layerNo="
            + this.getLayerNo()
            + ", name="
            + this.getName()
            + ", capacityDesc="
            + this.getCapacityDesc()
            + ", status="
            + this.getStatus()
            + ", remark="
            + this.getRemark()
            + ", version="
            + this.getVersion()
            + ", createdAt="
            + this.getCreatedAt()
            + ", updatedAt="
            + this.getUpdatedAt()
            + ")";
    }
}
