package com.dcim.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName(
    value = "workorder_item",
    autoResultMap = true
)
public class WorkorderItemEntity {
    @TableId(
        type = IdType.INPUT
    )
    private String itemNo;
    private String woNo;
    private String assetNo;
    private String srcRoom;
    private String srcRack;
    private Integer srcUnit;
    private Integer srcUHeight;
    private String srcRegion;
    private String dstRoom;
    private String dstRack;
    private Integer dstUnit;
    private String dstRegion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    
    public String getItemNo() {
        return this.itemNo;
    }

    
    public String getWoNo() {
        return this.woNo;
    }

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public String getSrcRoom() {
        return this.srcRoom;
    }

    
    public String getSrcRack() {
        return this.srcRack;
    }

    
    public Integer getSrcUnit() {
        return this.srcUnit;
    }

    
    public Integer getSrcUHeight() {
        return this.srcUHeight;
    }

    
    public String getSrcRegion() {
        return this.srcRegion;
    }

    
    public String getDstRoom() {
        return this.dstRoom;
    }

    
    public String getDstRack() {
        return this.dstRack;
    }

    
    public Integer getDstUnit() {
        return this.dstUnit;
    }

    
    public String getDstRegion() {
        return this.dstRegion;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    
    public void setItemNo(final String itemNo) {
        this.itemNo = itemNo;
    }

    
    public void setWoNo(final String woNo) {
        this.woNo = woNo;
    }

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
    }

    
    public void setSrcRoom(final String srcRoom) {
        this.srcRoom = srcRoom;
    }

    
    public void setSrcRack(final String srcRack) {
        this.srcRack = srcRack;
    }

    
    public void setSrcUnit(final Integer srcUnit) {
        this.srcUnit = srcUnit;
    }

    
    public void setSrcUHeight(final Integer srcUHeight) {
        this.srcUHeight = srcUHeight;
    }

    
    public void setSrcRegion(final String srcRegion) {
        this.srcRegion = srcRegion;
    }

    
    public void setDstRoom(final String dstRoom) {
        this.dstRoom = dstRoom;
    }

    
    public void setDstRack(final String dstRack) {
        this.dstRack = dstRack;
    }

    
    public void setDstUnit(final Integer dstUnit) {
        this.dstUnit = dstUnit;
    }

    
    public void setDstRegion(final String dstRegion) {
        this.dstRegion = dstRegion;
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
        } else if (!(o instanceof WorkorderItemEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$srcUnit = this.getSrcUnit();
            Object other$srcUnit = other.getSrcUnit();
            if (this$srcUnit == null ? other$srcUnit == null : this$srcUnit.equals(other$srcUnit)) {
                Object this$srcUHeight = this.getSrcUHeight();
                Object other$srcUHeight = other.getSrcUHeight();
                if (this$srcUHeight == null ? other$srcUHeight == null : this$srcUHeight.equals(other$srcUHeight)) {
                    Object this$dstUnit = this.getDstUnit();
                    Object other$dstUnit = other.getDstUnit();
                    if (this$dstUnit == null ? other$dstUnit == null : this$dstUnit.equals(other$dstUnit)) {
                        Object this$itemNo = this.getItemNo();
                        Object other$itemNo = other.getItemNo();
                        if (this$itemNo == null ? other$itemNo == null : this$itemNo.equals(other$itemNo)) {
                            Object this$woNo = this.getWoNo();
                            Object other$woNo = other.getWoNo();
                            if (this$woNo == null ? other$woNo == null : this$woNo.equals(other$woNo)) {
                                Object this$assetNo = this.getAssetNo();
                                Object other$assetNo = other.getAssetNo();
                                if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                                    Object this$srcRoom = this.getSrcRoom();
                                    Object other$srcRoom = other.getSrcRoom();
                                    if (this$srcRoom == null ? other$srcRoom == null : this$srcRoom.equals(other$srcRoom)) {
                                        Object this$srcRack = this.getSrcRack();
                                        Object other$srcRack = other.getSrcRack();
                                        if (this$srcRack == null ? other$srcRack == null : this$srcRack.equals(other$srcRack)) {
                                            Object this$srcRegion = this.getSrcRegion();
                                            Object other$srcRegion = other.getSrcRegion();
                                            if (this$srcRegion == null ? other$srcRegion == null : this$srcRegion.equals(other$srcRegion)) {
                                                Object this$dstRoom = this.getDstRoom();
                                                Object other$dstRoom = other.getDstRoom();
                                                if (this$dstRoom == null ? other$dstRoom == null : this$dstRoom.equals(other$dstRoom)) {
                                                    Object this$dstRack = this.getDstRack();
                                                    Object other$dstRack = other.getDstRack();
                                                    if (this$dstRack == null ? other$dstRack == null : this$dstRack.equals(other$dstRack)) {
                                                        Object this$dstRegion = this.getDstRegion();
                                                        Object other$dstRegion = other.getDstRegion();
                                                        if (this$dstRegion == null ? other$dstRegion == null : this$dstRegion.equals(other$dstRegion)) {
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
        return other instanceof WorkorderItemEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $srcUnit = this.getSrcUnit();
        result = result * 59 + ($srcUnit == null ? 43 : $srcUnit.hashCode());
        Object $srcUHeight = this.getSrcUHeight();
        result = result * 59 + ($srcUHeight == null ? 43 : $srcUHeight.hashCode());
        Object $dstUnit = this.getDstUnit();
        result = result * 59 + ($dstUnit == null ? 43 : $dstUnit.hashCode());
        Object $itemNo = this.getItemNo();
        result = result * 59 + ($itemNo == null ? 43 : $itemNo.hashCode());
        Object $woNo = this.getWoNo();
        result = result * 59 + ($woNo == null ? 43 : $woNo.hashCode());
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $srcRoom = this.getSrcRoom();
        result = result * 59 + ($srcRoom == null ? 43 : $srcRoom.hashCode());
        Object $srcRack = this.getSrcRack();
        result = result * 59 + ($srcRack == null ? 43 : $srcRack.hashCode());
        Object $srcRegion = this.getSrcRegion();
        result = result * 59 + ($srcRegion == null ? 43 : $srcRegion.hashCode());
        Object $dstRoom = this.getDstRoom();
        result = result * 59 + ($dstRoom == null ? 43 : $dstRoom.hashCode());
        Object $dstRack = this.getDstRack();
        result = result * 59 + ($dstRack == null ? 43 : $dstRack.hashCode());
        Object $dstRegion = this.getDstRegion();
        result = result * 59 + ($dstRegion == null ? 43 : $dstRegion.hashCode());
        Object $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
        Object $updatedAt = this.getUpdatedAt();
        return result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "WorkorderItemEntity(itemNo="
            + this.getItemNo()
            + ", woNo="
            + this.getWoNo()
            + ", assetNo="
            + this.getAssetNo()
            + ", srcRoom="
            + this.getSrcRoom()
            + ", srcRack="
            + this.getSrcRack()
            + ", srcUnit="
            + this.getSrcUnit()
            + ", srcUHeight="
            + this.getSrcUHeight()
            + ", srcRegion="
            + this.getSrcRegion()
            + ", dstRoom="
            + this.getDstRoom()
            + ", dstRack="
            + this.getDstRack()
            + ", dstUnit="
            + this.getDstUnit()
            + ", dstRegion="
            + this.getDstRegion()
            + ", createdAt="
            + this.getCreatedAt()
            + ", updatedAt="
            + this.getUpdatedAt()
            + ")";
    }
}
