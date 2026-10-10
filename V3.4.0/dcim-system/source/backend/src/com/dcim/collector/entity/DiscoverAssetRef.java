package com.dcim.collector.entity;


public class DiscoverAssetRef {
    private String assetNo;
    private String name;
    private String sn;
    private String modelCode;
    private String mgmtIp;

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getSn() {
        return this.sn;
    }

    
    public String getModelCode() {
        return this.modelCode;
    }

    
    public String getMgmtIp() {
        return this.mgmtIp;
    }

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setSn(final String sn) {
        this.sn = sn;
    }

    
    public void setModelCode(final String modelCode) {
        this.modelCode = modelCode;
    }

    
    public void setMgmtIp(final String mgmtIp) {
        this.mgmtIp = mgmtIp;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof DiscoverAssetRef other)) {
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
                    Object this$sn = this.getSn();
                    Object other$sn = other.getSn();
                    if (this$sn == null ? other$sn == null : this$sn.equals(other$sn)) {
                        Object this$modelCode = this.getModelCode();
                        Object other$modelCode = other.getModelCode();
                        if (this$modelCode == null ? other$modelCode == null : this$modelCode.equals(other$modelCode)) {
                            Object this$mgmtIp = this.getMgmtIp();
                            Object other$mgmtIp = other.getMgmtIp();
                            return this$mgmtIp == null ? other$mgmtIp == null : this$mgmtIp.equals(other$mgmtIp);
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
        return other instanceof DiscoverAssetRef;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $sn = this.getSn();
        result = result * 59 + ($sn == null ? 43 : $sn.hashCode());
        Object $modelCode = this.getModelCode();
        result = result * 59 + ($modelCode == null ? 43 : $modelCode.hashCode());
        Object $mgmtIp = this.getMgmtIp();
        return result * 59 + ($mgmtIp == null ? 43 : $mgmtIp.hashCode());
    }

    
    @Override
    public String toString() {
        return "DiscoverAssetRef(assetNo="
            + this.getAssetNo()
            + ", name="
            + this.getName()
            + ", sn="
            + this.getSn()
            + ", modelCode="
            + this.getModelCode()
            + ", mgmtIp="
            + this.getMgmtIp()
            + ")";
    }
}
