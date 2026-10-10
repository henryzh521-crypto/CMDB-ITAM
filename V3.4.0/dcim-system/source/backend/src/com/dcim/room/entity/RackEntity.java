package com.dcim.room.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName(
    value = "rack",
    autoResultMap = true
)
public class RackEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String rackCode;
    private String roomCode;
    private String locationName;
    private String rowNo;
    private Integer colNo;
    private String orientation;
    private Integer uCapacity;
    private BigDecimal powerLimitKw;
    private Integer pduCount;
    private String coolingType;
    private String manager;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getRackCode() {
        return this.rackCode;
    }

    
    public String getRoomCode() {
        return this.roomCode;
    }

    
    public String getLocationName() {
        return this.locationName;
    }

    
    public String getRowNo() {
        return this.rowNo;
    }

    
    public Integer getColNo() {
        return this.colNo;
    }

    
    public String getOrientation() {
        return this.orientation;
    }

    
    public Integer getUCapacity() {
        return this.uCapacity;
    }

    
    public BigDecimal getPowerLimitKw() {
        return this.powerLimitKw;
    }

    
    public Integer getPduCount() {
        return this.pduCount;
    }

    
    public String getCoolingType() {
        return this.coolingType;
    }

    
    public String getManager() {
        return this.manager;
    }

    
    public String getRemark() {
        return this.remark;
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

    
    public void setRackCode(final String rackCode) {
        this.rackCode = rackCode;
    }

    
    public void setRoomCode(final String roomCode) {
        this.roomCode = roomCode;
    }

    
    public void setLocationName(final String locationName) {
        this.locationName = locationName;
    }

    
    public void setRowNo(final String rowNo) {
        this.rowNo = rowNo;
    }

    
    public void setColNo(final Integer colNo) {
        this.colNo = colNo;
    }

    
    public void setOrientation(final String orientation) {
        this.orientation = orientation;
    }

    
    public void setUCapacity(final Integer uCapacity) {
        this.uCapacity = uCapacity;
    }

    
    public void setPowerLimitKw(final BigDecimal powerLimitKw) {
        this.powerLimitKw = powerLimitKw;
    }

    
    public void setPduCount(final Integer pduCount) {
        this.pduCount = pduCount;
    }

    
    public void setCoolingType(final String coolingType) {
        this.coolingType = coolingType;
    }

    
    public void setManager(final String manager) {
        this.manager = manager;
    }

    
    public void setRemark(final String remark) {
        this.remark = remark;
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
        } else if (!(o instanceof RackEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$colNo = this.getColNo();
                Object other$colNo = other.getColNo();
                if (this$colNo == null ? other$colNo == null : this$colNo.equals(other$colNo)) {
                    Object this$uCapacity = this.getUCapacity();
                    Object other$uCapacity = other.getUCapacity();
                    if (this$uCapacity == null ? other$uCapacity == null : this$uCapacity.equals(other$uCapacity)) {
                        Object this$pduCount = this.getPduCount();
                        Object other$pduCount = other.getPduCount();
                        if (this$pduCount == null ? other$pduCount == null : this$pduCount.equals(other$pduCount)) {
                            Object this$rackCode = this.getRackCode();
                            Object other$rackCode = other.getRackCode();
                            if (this$rackCode == null ? other$rackCode == null : this$rackCode.equals(other$rackCode)) {
                                Object this$roomCode = this.getRoomCode();
                                Object other$roomCode = other.getRoomCode();
                                if (this$roomCode == null ? other$roomCode == null : this$roomCode.equals(other$roomCode)) {
                                    Object this$locationName = this.getLocationName();
                                    Object other$locationName = other.getLocationName();
                                    if (this$locationName == null ? other$locationName == null : this$locationName.equals(other$locationName)) {
                                        Object this$rowNo = this.getRowNo();
                                        Object other$rowNo = other.getRowNo();
                                        if (this$rowNo == null ? other$rowNo == null : this$rowNo.equals(other$rowNo)) {
                                            Object this$orientation = this.getOrientation();
                                            Object other$orientation = other.getOrientation();
                                            if (this$orientation == null ? other$orientation == null : this$orientation.equals(other$orientation)) {
                                                Object this$powerLimitKw = this.getPowerLimitKw();
                                                Object other$powerLimitKw = other.getPowerLimitKw();
                                                if (this$powerLimitKw == null ? other$powerLimitKw == null : this$powerLimitKw.equals(other$powerLimitKw)) {
                                                    Object this$coolingType = this.getCoolingType();
                                                    Object other$coolingType = other.getCoolingType();
                                                    if (this$coolingType == null ? other$coolingType == null : this$coolingType.equals(other$coolingType)) {
                                                        Object this$manager = this.getManager();
                                                        Object other$manager = other.getManager();
                                                        if (this$manager == null ? other$manager == null : this$manager.equals(other$manager)) {
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
            } else {
                return false;
            }
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof RackEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $colNo = this.getColNo();
        result = result * 59 + ($colNo == null ? 43 : $colNo.hashCode());
        Object $uCapacity = this.getUCapacity();
        result = result * 59 + ($uCapacity == null ? 43 : $uCapacity.hashCode());
        Object $pduCount = this.getPduCount();
        result = result * 59 + ($pduCount == null ? 43 : $pduCount.hashCode());
        Object $rackCode = this.getRackCode();
        result = result * 59 + ($rackCode == null ? 43 : $rackCode.hashCode());
        Object $roomCode = this.getRoomCode();
        result = result * 59 + ($roomCode == null ? 43 : $roomCode.hashCode());
        Object $locationName = this.getLocationName();
        result = result * 59 + ($locationName == null ? 43 : $locationName.hashCode());
        Object $rowNo = this.getRowNo();
        result = result * 59 + ($rowNo == null ? 43 : $rowNo.hashCode());
        Object $orientation = this.getOrientation();
        result = result * 59 + ($orientation == null ? 43 : $orientation.hashCode());
        Object $powerLimitKw = this.getPowerLimitKw();
        result = result * 59 + ($powerLimitKw == null ? 43 : $powerLimitKw.hashCode());
        Object $coolingType = this.getCoolingType();
        result = result * 59 + ($coolingType == null ? 43 : $coolingType.hashCode());
        Object $manager = this.getManager();
        result = result * 59 + ($manager == null ? 43 : $manager.hashCode());
        Object $remark = this.getRemark();
        result = result * 59 + ($remark == null ? 43 : $remark.hashCode());
        Object $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
        Object $updatedAt = this.getUpdatedAt();
        return result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "RackEntity(id="
            + this.getId()
            + ", rackCode="
            + this.getRackCode()
            + ", roomCode="
            + this.getRoomCode()
            + ", locationName="
            + this.getLocationName()
            + ", rowNo="
            + this.getRowNo()
            + ", colNo="
            + this.getColNo()
            + ", orientation="
            + this.getOrientation()
            + ", uCapacity="
            + this.getUCapacity()
            + ", powerLimitKw="
            + this.getPowerLimitKw()
            + ", pduCount="
            + this.getPduCount()
            + ", coolingType="
            + this.getCoolingType()
            + ", manager="
            + this.getManager()
            + ", remark="
            + this.getRemark()
            + ", createdAt="
            + this.getCreatedAt()
            + ", updatedAt="
            + this.getUpdatedAt()
            + ")";
    }
}
