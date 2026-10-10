package com.dcim.report.dto;

import java.math.BigDecimal;

public class WoCostRow {
    private String woNo;
    private String woType;
    private String title;
    private String status;
    private BigDecimal laborValue;
    private BigDecimal serviceValue;
    private BigDecimal otherValue;
    private BigDecimal partsValue;
    private BigDecimal totalValue;

    
    public String getWoNo() {
        return this.woNo;
    }

    
    public String getWoType() {
        return this.woType;
    }

    
    public String getTitle() {
        return this.title;
    }

    
    public String getStatus() {
        return this.status;
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

    
    public BigDecimal getTotalValue() {
        return this.totalValue;
    }

    
    public void setWoNo(final String woNo) {
        this.woNo = woNo;
    }

    
    public void setWoType(final String woType) {
        this.woType = woType;
    }

    
    public void setTitle(final String title) {
        this.title = title;
    }

    
    public void setStatus(final String status) {
        this.status = status;
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

    
    public void setTotalValue(final BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof WoCostRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$woNo = this.getWoNo();
            Object other$woNo = other.getWoNo();
            if (this$woNo == null ? other$woNo == null : this$woNo.equals(other$woNo)) {
                Object this$woType = this.getWoType();
                Object other$woType = other.getWoType();
                if (this$woType == null ? other$woType == null : this$woType.equals(other$woType)) {
                    Object this$title = this.getTitle();
                    Object other$title = other.getTitle();
                    if (this$title == null ? other$title == null : this$title.equals(other$title)) {
                        Object this$status = this.getStatus();
                        Object other$status = other.getStatus();
                        if (this$status == null ? other$status == null : this$status.equals(other$status)) {
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
                                        if (this$partsValue == null ? other$partsValue == null : this$partsValue.equals(other$partsValue)) {
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
        return other instanceof WoCostRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $woNo = this.getWoNo();
        result = result * 59 + ($woNo == null ? 43 : $woNo.hashCode());
        Object $woType = this.getWoType();
        result = result * 59 + ($woType == null ? 43 : $woType.hashCode());
        Object $title = this.getTitle();
        result = result * 59 + ($title == null ? 43 : $title.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $laborValue = this.getLaborValue();
        result = result * 59 + ($laborValue == null ? 43 : $laborValue.hashCode());
        Object $serviceValue = this.getServiceValue();
        result = result * 59 + ($serviceValue == null ? 43 : $serviceValue.hashCode());
        Object $otherValue = this.getOtherValue();
        result = result * 59 + ($otherValue == null ? 43 : $otherValue.hashCode());
        Object $partsValue = this.getPartsValue();
        result = result * 59 + ($partsValue == null ? 43 : $partsValue.hashCode());
        Object $totalValue = this.getTotalValue();
        return result * 59 + ($totalValue == null ? 43 : $totalValue.hashCode());
    }

    
    @Override
    public String toString() {
        return "WoCostRow(woNo="
            + this.getWoNo()
            + ", woType="
            + this.getWoType()
            + ", title="
            + this.getTitle()
            + ", status="
            + this.getStatus()
            + ", laborValue="
            + this.getLaborValue()
            + ", serviceValue="
            + this.getServiceValue()
            + ", otherValue="
            + this.getOtherValue()
            + ", partsValue="
            + this.getPartsValue()
            + ", totalValue="
            + this.getTotalValue()
            + ")";
    }
}
