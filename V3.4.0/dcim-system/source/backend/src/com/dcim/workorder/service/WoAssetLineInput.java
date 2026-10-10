package com.dcim.workorder.service;


public class WoAssetLineInput {
    private String assetNo;
    private String dstRoom;
    private String dstRack;
    private Integer dstUnit;

    
    public String getAssetNo() {
        return this.assetNo;
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

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
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

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof WoAssetLineInput other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$dstUnit = this.getDstUnit();
            Object other$dstUnit = other.getDstUnit();
            if (this$dstUnit == null ? other$dstUnit == null : this$dstUnit.equals(other$dstUnit)) {
                Object this$assetNo = this.getAssetNo();
                Object other$assetNo = other.getAssetNo();
                if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                    Object this$dstRoom = this.getDstRoom();
                    Object other$dstRoom = other.getDstRoom();
                    if (this$dstRoom == null ? other$dstRoom == null : this$dstRoom.equals(other$dstRoom)) {
                        Object this$dstRack = this.getDstRack();
                        Object other$dstRack = other.getDstRack();
                        return this$dstRack == null ? other$dstRack == null : this$dstRack.equals(other$dstRack);
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
        return other instanceof WoAssetLineInput;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $dstUnit = this.getDstUnit();
        result = result * 59 + ($dstUnit == null ? 43 : $dstUnit.hashCode());
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $dstRoom = this.getDstRoom();
        result = result * 59 + ($dstRoom == null ? 43 : $dstRoom.hashCode());
        Object $dstRack = this.getDstRack();
        return result * 59 + ($dstRack == null ? 43 : $dstRack.hashCode());
    }

    
    @Override
    public String toString() {
        return "WoAssetLineInput(assetNo="
            + this.getAssetNo()
            + ", dstRoom="
            + this.getDstRoom()
            + ", dstRack="
            + this.getDstRack()
            + ", dstUnit="
            + this.getDstUnit()
            + ")";
    }
}
