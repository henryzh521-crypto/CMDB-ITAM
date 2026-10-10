package com.dcim.warehouse.entity;


public class InventoryAssetRow {
    private String assetNo;
    private String name;
    private String sn;
    private String categoryCode;
    private String modelCode;
    private String rackCode;
    private Integer unitNo;
    private Integer uHeight;
    private String statusBiz;

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getSn() {
        return this.sn;
    }

    
    public String getCategoryCode() {
        return this.categoryCode;
    }

    
    public String getModelCode() {
        return this.modelCode;
    }

    
    public String getRackCode() {
        return this.rackCode;
    }

    
    public Integer getUnitNo() {
        return this.unitNo;
    }

    
    public Integer getUHeight() {
        return this.uHeight;
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

    
    public void setSn(final String sn) {
        this.sn = sn;
    }

    
    public void setCategoryCode(final String categoryCode) {
        this.categoryCode = categoryCode;
    }

    
    public void setModelCode(final String modelCode) {
        this.modelCode = modelCode;
    }

    
    public void setRackCode(final String rackCode) {
        this.rackCode = rackCode;
    }

    
    public void setUnitNo(final Integer unitNo) {
        this.unitNo = unitNo;
    }

    
    public void setUHeight(final Integer uHeight) {
        this.uHeight = uHeight;
    }

    
    public void setStatusBiz(final String statusBiz) {
        this.statusBiz = statusBiz;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof InventoryAssetRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$unitNo = this.getUnitNo();
            Object other$unitNo = other.getUnitNo();
            if (this$unitNo == null ? other$unitNo == null : this$unitNo.equals(other$unitNo)) {
                Object this$uHeight = this.getUHeight();
                Object other$uHeight = other.getUHeight();
                if (this$uHeight == null ? other$uHeight == null : this$uHeight.equals(other$uHeight)) {
                    Object this$assetNo = this.getAssetNo();
                    Object other$assetNo = other.getAssetNo();
                    if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                        Object this$name = this.getName();
                        Object other$name = other.getName();
                        if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                            Object this$sn = this.getSn();
                            Object other$sn = other.getSn();
                            if (this$sn == null ? other$sn == null : this$sn.equals(other$sn)) {
                                Object this$categoryCode = this.getCategoryCode();
                                Object other$categoryCode = other.getCategoryCode();
                                if (this$categoryCode == null ? other$categoryCode == null : this$categoryCode.equals(other$categoryCode)) {
                                    Object this$modelCode = this.getModelCode();
                                    Object other$modelCode = other.getModelCode();
                                    if (this$modelCode == null ? other$modelCode == null : this$modelCode.equals(other$modelCode)) {
                                        Object this$rackCode = this.getRackCode();
                                        Object other$rackCode = other.getRackCode();
                                        if (this$rackCode == null ? other$rackCode == null : this$rackCode.equals(other$rackCode)) {
                                            Object this$statusBiz = this.getStatusBiz();
                                            Object other$statusBiz = other.getStatusBiz();
                                            return this$statusBiz == null ? other$statusBiz == null : this$statusBiz.equals(other$statusBiz);
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
        return other instanceof InventoryAssetRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $unitNo = this.getUnitNo();
        result = result * 59 + ($unitNo == null ? 43 : $unitNo.hashCode());
        Object $uHeight = this.getUHeight();
        result = result * 59 + ($uHeight == null ? 43 : $uHeight.hashCode());
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $sn = this.getSn();
        result = result * 59 + ($sn == null ? 43 : $sn.hashCode());
        Object $categoryCode = this.getCategoryCode();
        result = result * 59 + ($categoryCode == null ? 43 : $categoryCode.hashCode());
        Object $modelCode = this.getModelCode();
        result = result * 59 + ($modelCode == null ? 43 : $modelCode.hashCode());
        Object $rackCode = this.getRackCode();
        result = result * 59 + ($rackCode == null ? 43 : $rackCode.hashCode());
        Object $statusBiz = this.getStatusBiz();
        return result * 59 + ($statusBiz == null ? 43 : $statusBiz.hashCode());
    }

    
    @Override
    public String toString() {
        return "InventoryAssetRow(assetNo="
            + this.getAssetNo()
            + ", name="
            + this.getName()
            + ", sn="
            + this.getSn()
            + ", categoryCode="
            + this.getCategoryCode()
            + ", modelCode="
            + this.getModelCode()
            + ", rackCode="
            + this.getRackCode()
            + ", unitNo="
            + this.getUnitNo()
            + ", uHeight="
            + this.getUHeight()
            + ", statusBiz="
            + this.getStatusBiz()
            + ")";
    }
}
