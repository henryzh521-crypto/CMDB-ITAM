package com.dcim.report.dto;


public class SlaRow {
    private Long total;
    private Long responded;
    private Long overdue;

    
    public Long getTotal() {
        return this.total;
    }

    
    public Long getResponded() {
        return this.responded;
    }

    
    public Long getOverdue() {
        return this.overdue;
    }

    
    public void setTotal(final Long total) {
        this.total = total;
    }

    
    public void setResponded(final Long responded) {
        this.responded = responded;
    }

    
    public void setOverdue(final Long overdue) {
        this.overdue = overdue;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof SlaRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$total = this.getTotal();
            Object other$total = other.getTotal();
            if (this$total == null ? other$total == null : this$total.equals(other$total)) {
                Object this$responded = this.getResponded();
                Object other$responded = other.getResponded();
                if (this$responded == null ? other$responded == null : this$responded.equals(other$responded)) {
                    Object this$overdue = this.getOverdue();
                    Object other$overdue = other.getOverdue();
                    return this$overdue == null ? other$overdue == null : this$overdue.equals(other$overdue);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof SlaRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $total = this.getTotal();
        result = result * 59 + ($total == null ? 43 : $total.hashCode());
        Object $responded = this.getResponded();
        result = result * 59 + ($responded == null ? 43 : $responded.hashCode());
        Object $overdue = this.getOverdue();
        return result * 59 + ($overdue == null ? 43 : $overdue.hashCode());
    }

    
    @Override
    public String toString() {
        return "SlaRow(total=" + this.getTotal() + ", responded=" + this.getResponded() + ", overdue=" + this.getOverdue() + ")";
    }
}
