package com.dcim.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("warehouse")
public class WarehouseEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String warehouseCode;
    private String name;
    private String warehouseType;
    private String roomCode;
    private String address;
    private String manager;
    private String capacityDesc;
    private String status;
    private Boolean isDefault;
    private String remark;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getWarehouseCode() {
        return this.warehouseCode;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getWarehouseType() {
        return this.warehouseType;
    }

    
    public String getRoomCode() {
        return this.roomCode;
    }

    
    public String getAddress() {
        return this.address;
    }

    
    public String getManager() {
        return this.manager;
    }

    
    public String getCapacityDesc() {
        return this.capacityDesc;
    }

    
    public String getStatus() {
        return this.status;
    }

    
    public Boolean getIsDefault() {
        return this.isDefault;
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

    
    public void setWarehouseCode(final String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setWarehouseType(final String warehouseType) {
        this.warehouseType = warehouseType;
    }

    
    public void setRoomCode(final String roomCode) {
        this.roomCode = roomCode;
    }

    
    public void setAddress(final String address) {
        this.address = address;
    }

    
    public void setManager(final String manager) {
        this.manager = manager;
    }

    
    public void setCapacityDesc(final String capacityDesc) {
        this.capacityDesc = capacityDesc;
    }

    
    public void setStatus(final String status) {
        this.status = status;
    }

    
    public void setIsDefault(final Boolean isDefault) {
        this.isDefault = isDefault;
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
        } else if (!(o instanceof WarehouseEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$isDefault = this.getIsDefault();
                Object other$isDefault = other.getIsDefault();
                if (this$isDefault == null ? other$isDefault == null : this$isDefault.equals(other$isDefault)) {
                    Object this$version = this.getVersion();
                    Object other$version = other.getVersion();
                    if (this$version == null ? other$version == null : this$version.equals(other$version)) {
                        Object this$warehouseCode = this.getWarehouseCode();
                        Object other$warehouseCode = other.getWarehouseCode();
                        if (this$warehouseCode == null ? other$warehouseCode == null : this$warehouseCode.equals(other$warehouseCode)) {
                            Object this$name = this.getName();
                            Object other$name = other.getName();
                            if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                                Object this$warehouseType = this.getWarehouseType();
                                Object other$warehouseType = other.getWarehouseType();
                                if (this$warehouseType == null ? other$warehouseType == null : this$warehouseType.equals(other$warehouseType)) {
                                    Object this$roomCode = this.getRoomCode();
                                    Object other$roomCode = other.getRoomCode();
                                    if (this$roomCode == null ? other$roomCode == null : this$roomCode.equals(other$roomCode)) {
                                        Object this$address = this.getAddress();
                                        Object other$address = other.getAddress();
                                        if (this$address == null ? other$address == null : this$address.equals(other$address)) {
                                            Object this$manager = this.getManager();
                                            Object other$manager = other.getManager();
                                            if (this$manager == null ? other$manager == null : this$manager.equals(other$manager)) {
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
                                                                return this$updatedAt == null
                                                                    ? other$updatedAt == null
                                                                    : this$updatedAt.equals(other$updatedAt);
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
            } else {
                return false;
            }
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof WarehouseEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $isDefault = this.getIsDefault();
        result = result * 59 + ($isDefault == null ? 43 : $isDefault.hashCode());
        Object $version = this.getVersion();
        result = result * 59 + ($version == null ? 43 : $version.hashCode());
        Object $warehouseCode = this.getWarehouseCode();
        result = result * 59 + ($warehouseCode == null ? 43 : $warehouseCode.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $warehouseType = this.getWarehouseType();
        result = result * 59 + ($warehouseType == null ? 43 : $warehouseType.hashCode());
        Object $roomCode = this.getRoomCode();
        result = result * 59 + ($roomCode == null ? 43 : $roomCode.hashCode());
        Object $address = this.getAddress();
        result = result * 59 + ($address == null ? 43 : $address.hashCode());
        Object $manager = this.getManager();
        result = result * 59 + ($manager == null ? 43 : $manager.hashCode());
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
        return "WarehouseEntity(id="
            + this.getId()
            + ", warehouseCode="
            + this.getWarehouseCode()
            + ", name="
            + this.getName()
            + ", warehouseType="
            + this.getWarehouseType()
            + ", roomCode="
            + this.getRoomCode()
            + ", address="
            + this.getAddress()
            + ", manager="
            + this.getManager()
            + ", capacityDesc="
            + this.getCapacityDesc()
            + ", status="
            + this.getStatus()
            + ", isDefault="
            + this.getIsDefault()
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
