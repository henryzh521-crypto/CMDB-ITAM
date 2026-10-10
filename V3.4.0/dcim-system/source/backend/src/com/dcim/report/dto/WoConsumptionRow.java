package com.dcim.report.dto;

import java.math.BigDecimal;

public class WoConsumptionRow {
    private String woNo;
    private String woType;
    private String title;
    private String status;
    private Integer logCount;
    private Integer materialCount;
    private Integer qty;
    private BigDecimal value;

    
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

    
    public Integer getLogCount() {
        return this.logCount;
    }

    
    public Integer getMaterialCount() {
        return this.materialCount;
    }

    
    public Integer getQty() {
        return this.qty;
    }

    
    public BigDecimal getValue() {
        return this.value;
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

    
    public void setLogCount(final Integer logCount) {
        this.logCount = logCount;
    }

    
    public void setMaterialCount(final Integer materialCount) {
        this.materialCount = materialCount;
    }

    
    public void setQty(final Integer qty) {
        this.qty = qty;
    }

    
    public void setValue(final BigDecimal value) {
        this.value = value;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof WoConsumptionRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$logCount = this.getLogCount();
            Object other$logCount = other.getLogCount();
            if (this$logCount == null ? other$logCount == null : this$logCount.equals(other$logCount)) {
                Object this$materialCount = this.getMaterialCount();
                Object other$materialCount = other.getMaterialCount();
                if (this$materialCount == null ? other$materialCount == null : this$materialCount.equals(other$materialCount)) {
                    Object this$qty = this.getQty();
                    Object other$qty = other.getQty();
                    if (this$qty == null ? other$qty == null : this$qty.equals(other$qty)) {
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
                                        Object this$value = this.getValue();
                                        Object other$value = other.getValue();
                                        return this$value == null ? other$value == null : this$value.equals(other$value);
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
        return other instanceof WoConsumptionRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $logCount = this.getLogCount();
        result = result * 59 + ($logCount == null ? 43 : $logCount.hashCode());
        Object $materialCount = this.getMaterialCount();
        result = result * 59 + ($materialCount == null ? 43 : $materialCount.hashCode());
        Object $qty = this.getQty();
        result = result * 59 + ($qty == null ? 43 : $qty.hashCode());
        Object $woNo = this.getWoNo();
        result = result * 59 + ($woNo == null ? 43 : $woNo.hashCode());
        Object $woType = this.getWoType();
        result = result * 59 + ($woType == null ? 43 : $woType.hashCode());
        Object $title = this.getTitle();
        result = result * 59 + ($title == null ? 43 : $title.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $value = this.getValue();
        return result * 59 + ($value == null ? 43 : $value.hashCode());
    }

    
    @Override
    public String toString() {
        return "WoConsumptionRow(woNo="
            + this.getWoNo()
            + ", woType="
            + this.getWoType()
            + ", title="
            + this.getTitle()
            + ", status="
            + this.getStatus()
            + ", logCount="
            + this.getLogCount()
            + ", materialCount="
            + this.getMaterialCount()
            + ", qty="
            + this.getQty()
            + ", value="
            + this.getValue()
            + ")";
    }
}
