package com.dcim.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DepreciationRow {
    private String assetNo;
    private String assetName;
    private LocalDate period;
    private BigDecimal openingNetValue;
    private BigDecimal depAmount;
    private BigDecimal accumulatedDep;
    private BigDecimal closingNetValue;

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public String getAssetName() {
        return this.assetName;
    }

    
    public LocalDate getPeriod() {
        return this.period;
    }

    
    public BigDecimal getOpeningNetValue() {
        return this.openingNetValue;
    }

    
    public BigDecimal getDepAmount() {
        return this.depAmount;
    }

    
    public BigDecimal getAccumulatedDep() {
        return this.accumulatedDep;
    }

    
    public BigDecimal getClosingNetValue() {
        return this.closingNetValue;
    }

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
    }

    
    public void setAssetName(final String assetName) {
        this.assetName = assetName;
    }

    
    public void setPeriod(final LocalDate period) {
        this.period = period;
    }

    
    public void setOpeningNetValue(final BigDecimal openingNetValue) {
        this.openingNetValue = openingNetValue;
    }

    
    public void setDepAmount(final BigDecimal depAmount) {
        this.depAmount = depAmount;
    }

    
    public void setAccumulatedDep(final BigDecimal accumulatedDep) {
        this.accumulatedDep = accumulatedDep;
    }

    
    public void setClosingNetValue(final BigDecimal closingNetValue) {
        this.closingNetValue = closingNetValue;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof DepreciationRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$assetNo = this.getAssetNo();
            Object other$assetNo = other.getAssetNo();
            if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                Object this$assetName = this.getAssetName();
                Object other$assetName = other.getAssetName();
                if (this$assetName == null ? other$assetName == null : this$assetName.equals(other$assetName)) {
                    Object this$period = this.getPeriod();
                    Object other$period = other.getPeriod();
                    if (this$period == null ? other$period == null : this$period.equals(other$period)) {
                        Object this$openingNetValue = this.getOpeningNetValue();
                        Object other$openingNetValue = other.getOpeningNetValue();
                        if (this$openingNetValue == null ? other$openingNetValue == null : this$openingNetValue.equals(other$openingNetValue)) {
                            Object this$depAmount = this.getDepAmount();
                            Object other$depAmount = other.getDepAmount();
                            if (this$depAmount == null ? other$depAmount == null : this$depAmount.equals(other$depAmount)) {
                                Object this$accumulatedDep = this.getAccumulatedDep();
                                Object other$accumulatedDep = other.getAccumulatedDep();
                                if (this$accumulatedDep == null ? other$accumulatedDep == null : this$accumulatedDep.equals(other$accumulatedDep)) {
                                    Object this$closingNetValue = this.getClosingNetValue();
                                    Object other$closingNetValue = other.getClosingNetValue();
                                    return this$closingNetValue == null ? other$closingNetValue == null : this$closingNetValue.equals(other$closingNetValue);
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
        return other instanceof DepreciationRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $assetName = this.getAssetName();
        result = result * 59 + ($assetName == null ? 43 : $assetName.hashCode());
        Object $period = this.getPeriod();
        result = result * 59 + ($period == null ? 43 : $period.hashCode());
        Object $openingNetValue = this.getOpeningNetValue();
        result = result * 59 + ($openingNetValue == null ? 43 : $openingNetValue.hashCode());
        Object $depAmount = this.getDepAmount();
        result = result * 59 + ($depAmount == null ? 43 : $depAmount.hashCode());
        Object $accumulatedDep = this.getAccumulatedDep();
        result = result * 59 + ($accumulatedDep == null ? 43 : $accumulatedDep.hashCode());
        Object $closingNetValue = this.getClosingNetValue();
        return result * 59 + ($closingNetValue == null ? 43 : $closingNetValue.hashCode());
    }

    
    @Override
    public String toString() {
        return "DepreciationRow(assetNo="
            + this.getAssetNo()
            + ", assetName="
            + this.getAssetName()
            + ", period="
            + this.getPeriod()
            + ", openingNetValue="
            + this.getOpeningNetValue()
            + ", depAmount="
            + this.getDepAmount()
            + ", accumulatedDep="
            + this.getAccumulatedDep()
            + ", closingNetValue="
            + this.getClosingNetValue()
            + ")";
    }
}
