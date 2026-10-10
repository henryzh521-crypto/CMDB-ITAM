package com.dcim.report.dto;

import java.math.BigDecimal;

public class WoConsumptionSummaryRow {
    private Long woCount;
    private Long materialCount;
    private Integer totalQty;
    private BigDecimal totalValue;

    
    public Long getWoCount() {
        return this.woCount;
    }

    
    public Long getMaterialCount() {
        return this.materialCount;
    }

    
    public Integer getTotalQty() {
        return this.totalQty;
    }

    
    public BigDecimal getTotalValue() {
        return this.totalValue;
    }

    
    public void setWoCount(final Long woCount) {
        this.woCount = woCount;
    }

    
    public void setMaterialCount(final Long materialCount) {
        this.materialCount = materialCount;
    }

    
    public void setTotalQty(final Integer totalQty) {
        this.totalQty = totalQty;
    }

    
    public void setTotalValue(final BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof WoConsumptionSummaryRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$woCount = this.getWoCount();
            Object other$woCount = other.getWoCount();
            if (this$woCount == null ? other$woCount == null : this$woCount.equals(other$woCount)) {
                Object this$materialCount = this.getMaterialCount();
                Object other$materialCount = other.getMaterialCount();
                if (this$materialCount == null ? other$materialCount == null : this$materialCount.equals(other$materialCount)) {
                    Object this$totalQty = this.getTotalQty();
                    Object other$totalQty = other.getTotalQty();
                    if (this$totalQty == null ? other$totalQty == null : this$totalQty.equals(other$totalQty)) {
                        Object this$totalValue = this.getTotalValue();
                        Object other$totalValue = other.getTotalValue();
                        return this$totalValue == null ? other$totalValue == null : this$totalValue.equals(other$totalValue);
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
        return other instanceof WoConsumptionSummaryRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $woCount = this.getWoCount();
        result = result * 59 + ($woCount == null ? 43 : $woCount.hashCode());
        Object $materialCount = this.getMaterialCount();
        result = result * 59 + ($materialCount == null ? 43 : $materialCount.hashCode());
        Object $totalQty = this.getTotalQty();
        result = result * 59 + ($totalQty == null ? 43 : $totalQty.hashCode());
        Object $totalValue = this.getTotalValue();
        return result * 59 + ($totalValue == null ? 43 : $totalValue.hashCode());
    }

    
    @Override
    public String toString() {
        return "WoConsumptionSummaryRow(woCount="
            + this.getWoCount()
            + ", materialCount="
            + this.getMaterialCount()
            + ", totalQty="
            + this.getTotalQty()
            + ", totalValue="
            + this.getTotalValue()
            + ")";
    }
}
