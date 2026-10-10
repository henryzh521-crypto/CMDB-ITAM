package com.dcim.report.dto;

import java.math.BigDecimal;

public class WoCostSummaryRow {
    private Long woCount;
    private BigDecimal laborValue;
    private BigDecimal serviceValue;
    private BigDecimal otherValue;
    private BigDecimal partsValue;
    private Long partsWoCount;

    
    public Long getWoCount() {
        return this.woCount;
    }

    
    public BigDecimal getLaborValue() {
        return this.laborValue;
    }

    
    public BigDecimal getServiceValue() {
        return this.serviceValue;
    }

    
    public BigDecimal getOtherValue() {
        return this.otherValue;
    }

    
    public BigDecimal getPartsValue() {
        return this.partsValue;
    }

    
    public Long getPartsWoCount() {
        return this.partsWoCount;
    }

    
    public void setWoCount(final Long woCount) {
        this.woCount = woCount;
    }

    
    public void setLaborValue(final BigDecimal laborValue) {
        this.laborValue = laborValue;
    }

    
    public void setServiceValue(final BigDecimal serviceValue) {
        this.serviceValue = serviceValue;
    }

    
    public void setOtherValue(final BigDecimal otherValue) {
        this.otherValue = otherValue;
    }

    
    public void setPartsValue(final BigDecimal partsValue) {
        this.partsValue = partsValue;
    }

    
    public void setPartsWoCount(final Long partsWoCount) {
        this.partsWoCount = partsWoCount;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof WoCostSummaryRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$woCount = this.getWoCount();
            Object other$woCount = other.getWoCount();
            if (this$woCount == null ? other$woCount == null : this$woCount.equals(other$woCount)) {
                Object this$partsWoCount = this.getPartsWoCount();
                Object other$partsWoCount = other.getPartsWoCount();
                if (this$partsWoCount == null ? other$partsWoCount == null : this$partsWoCount.equals(other$partsWoCount)) {
                    Object this$laborValue = this.getLaborValue();
                    Object other$laborValue = other.getLaborValue();
                    if (this$laborValue == null ? other$laborValue == null : this$laborValue.equals(other$laborValue)) {
                        Object this$serviceValue = this.getServiceValue();
                        Object other$serviceValue = other.getServiceValue();
                        if (this$serviceValue == null ? other$serviceValue == null : this$serviceValue.equals(other$serviceValue)) {
                            Object this$otherValue = this.getOtherValue();
                            Object other$otherValue = other.getOtherValue();
                            if (this$otherValue == null ? other$otherValue == null : this$otherValue.equals(other$otherValue)) {
                                Object this$partsValue = this.getPartsValue();
                                Object other$partsValue = other.getPartsValue();
                                return this$partsValue == null ? other$partsValue == null : this$partsValue.equals(other$partsValue);
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
        return other instanceof WoCostSummaryRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $woCount = this.getWoCount();
        result = result * 59 + ($woCount == null ? 43 : $woCount.hashCode());
        Object $partsWoCount = this.getPartsWoCount();
        result = result * 59 + ($partsWoCount == null ? 43 : $partsWoCount.hashCode());
        Object $laborValue = this.getLaborValue();
        result = result * 59 + ($laborValue == null ? 43 : $laborValue.hashCode());
        Object $serviceValue = this.getServiceValue();
        result = result * 59 + ($serviceValue == null ? 43 : $serviceValue.hashCode());
        Object $otherValue = this.getOtherValue();
        result = result * 59 + ($otherValue == null ? 43 : $otherValue.hashCode());
        Object $partsValue = this.getPartsValue();
        return result * 59 + ($partsValue == null ? 43 : $partsValue.hashCode());
    }

    
    @Override
    public String toString() {
        return "WoCostSummaryRow(woCount="
            + this.getWoCount()
            + ", laborValue="
            + this.getLaborValue()
            + ", serviceValue="
            + this.getServiceValue()
            + ", otherValue="
            + this.getOtherValue()
            + ", partsValue="
            + this.getPartsValue()
            + ", partsWoCount="
            + this.getPartsWoCount()
            + ")";
    }
}
