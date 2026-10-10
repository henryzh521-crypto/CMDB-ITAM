package com.dcim.report.dto;

import java.math.BigDecimal;

public class WarehouseStockAggRow {
    private String warehouseCode;
    private String name;
    private String warehouseType;
    private String status;
    private Integer materialCount;
    private Integer stockQty;
    private BigDecimal stockValue;
    private Integer lowCount;
    private Integer outCount;

    
    public String getWarehouseCode() {
        return this.warehouseCode;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getWarehouseType() {
        return this.warehouseType;
    }

    
    public String getStatus() {
        return this.status;
    }

    
    public Integer getMaterialCount() {
        return this.materialCount;
    }

    
    public Integer getStockQty() {
        return this.stockQty;
    }

    
    public BigDecimal getStockValue() {
        return this.stockValue;
    }

    
    public Integer getLowCount() {
        return this.lowCount;
    }

    
    public Integer getOutCount() {
        return this.outCount;
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

    
    public void setStatus(final String status) {
        this.status = status;
    }

    
    public void setMaterialCount(final Integer materialCount) {
        this.materialCount = materialCount;
    }

    
    public void setStockQty(final Integer stockQty) {
        this.stockQty = stockQty;
    }

    
    public void setStockValue(final BigDecimal stockValue) {
        this.stockValue = stockValue;
    }

    
    public void setLowCount(final Integer lowCount) {
        this.lowCount = lowCount;
    }

    
    public void setOutCount(final Integer outCount) {
        this.outCount = outCount;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof WarehouseStockAggRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$materialCount = this.getMaterialCount();
            Object other$materialCount = other.getMaterialCount();
            if (this$materialCount == null ? other$materialCount == null : this$materialCount.equals(other$materialCount)) {
                Object this$stockQty = this.getStockQty();
                Object other$stockQty = other.getStockQty();
                if (this$stockQty == null ? other$stockQty == null : this$stockQty.equals(other$stockQty)) {
                    Object this$lowCount = this.getLowCount();
                    Object other$lowCount = other.getLowCount();
                    if (this$lowCount == null ? other$lowCount == null : this$lowCount.equals(other$lowCount)) {
                        Object this$outCount = this.getOutCount();
                        Object other$outCount = other.getOutCount();
                        if (this$outCount == null ? other$outCount == null : this$outCount.equals(other$outCount)) {
                            Object this$warehouseCode = this.getWarehouseCode();
                            Object other$warehouseCode = other.getWarehouseCode();
                            if (this$warehouseCode == null ? other$warehouseCode == null : this$warehouseCode.equals(other$warehouseCode)) {
                                Object this$name = this.getName();
                                Object other$name = other.getName();
                                if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                                    Object this$warehouseType = this.getWarehouseType();
                                    Object other$warehouseType = other.getWarehouseType();
                                    if (this$warehouseType == null ? other$warehouseType == null : this$warehouseType.equals(other$warehouseType)) {
                                        Object this$status = this.getStatus();
                                        Object other$status = other.getStatus();
                                        if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                                            Object this$stockValue = this.getStockValue();
                                            Object other$stockValue = other.getStockValue();
                                            return this$stockValue == null ? other$stockValue == null : this$stockValue.equals(other$stockValue);
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
        return other instanceof WarehouseStockAggRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $materialCount = this.getMaterialCount();
        result = result * 59 + ($materialCount == null ? 43 : $materialCount.hashCode());
        Object $stockQty = this.getStockQty();
        result = result * 59 + ($stockQty == null ? 43 : $stockQty.hashCode());
        Object $lowCount = this.getLowCount();
        result = result * 59 + ($lowCount == null ? 43 : $lowCount.hashCode());
        Object $outCount = this.getOutCount();
        result = result * 59 + ($outCount == null ? 43 : $outCount.hashCode());
        Object $warehouseCode = this.getWarehouseCode();
        result = result * 59 + ($warehouseCode == null ? 43 : $warehouseCode.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $warehouseType = this.getWarehouseType();
        result = result * 59 + ($warehouseType == null ? 43 : $warehouseType.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $stockValue = this.getStockValue();
        return result * 59 + ($stockValue == null ? 43 : $stockValue.hashCode());
    }

    
    @Override
    public String toString() {
        return "WarehouseStockAggRow(warehouseCode="
            + this.getWarehouseCode()
            + ", name="
            + this.getName()
            + ", warehouseType="
            + this.getWarehouseType()
            + ", status="
            + this.getStatus()
            + ", materialCount="
            + this.getMaterialCount()
            + ", stockQty="
            + this.getStockQty()
            + ", stockValue="
            + this.getStockValue()
            + ", lowCount="
            + this.getLowCount()
            + ", outCount="
            + this.getOutCount()
            + ")";
    }
}
