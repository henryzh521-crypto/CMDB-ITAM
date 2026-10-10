package com.dcim.report.dto;


public class RackUsageRow {
    private Long totalUnits;
    private Long usedUnits;

    
    public Long getTotalUnits() {
        return this.totalUnits;
    }

    
    public Long getUsedUnits() {
        return this.usedUnits;
    }

    
    public void setTotalUnits(final Long totalUnits) {
        this.totalUnits = totalUnits;
    }

    
    public void setUsedUnits(final Long usedUnits) {
        this.usedUnits = usedUnits;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof RackUsageRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$totalUnits = this.getTotalUnits();
            Object other$totalUnits = other.getTotalUnits();
            if (this$totalUnits == null ? other$totalUnits == null : this$totalUnits.equals(other$totalUnits)) {
                Object this$usedUnits = this.getUsedUnits();
                Object other$usedUnits = other.getUsedUnits();
                return this$usedUnits == null ? other$usedUnits == null : this$usedUnits.equals(other$usedUnits);
            } else {
                return false;
            }
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof RackUsageRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $totalUnits = this.getTotalUnits();
        result = result * 59 + ($totalUnits == null ? 43 : $totalUnits.hashCode());
        Object $usedUnits = this.getUsedUnits();
        return result * 59 + ($usedUnits == null ? 43 : $usedUnits.hashCode());
    }

    
    @Override
    public String toString() {
        return "RackUsageRow(totalUnits=" + this.getTotalUnits() + ", usedUnits=" + this.getUsedUnits() + ")";
    }
}
