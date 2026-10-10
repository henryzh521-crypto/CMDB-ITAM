package com.dcim.room.entity;


public class RoomAssetRef {
    private String assetNo;
    private String name;
    private String statusBiz;

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getStatusBiz() {
        return this.statusBiz;
    }

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setStatusBiz(final String statusBiz) {
        this.statusBiz = statusBiz;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof RoomAssetRef other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$assetNo = this.getAssetNo();
            Object other$assetNo = other.getAssetNo();
            if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                Object this$name = this.getName();
                Object other$name = other.getName();
                if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                    Object this$statusBiz = this.getStatusBiz();
                    Object other$statusBiz = other.getStatusBiz();
                    return this$statusBiz == null ? other$statusBiz == null : this$statusBiz.equals(other$statusBiz);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof RoomAssetRef;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $statusBiz = this.getStatusBiz();
        return result * 59 + ($statusBiz == null ? 43 : $statusBiz.hashCode());
    }

    
    @Override
    public String toString() {
        return "RoomAssetRef(assetNo=" + this.getAssetNo() + ", name=" + this.getName() + ", statusBiz=" + this.getStatusBiz() + ")";
    }
}
