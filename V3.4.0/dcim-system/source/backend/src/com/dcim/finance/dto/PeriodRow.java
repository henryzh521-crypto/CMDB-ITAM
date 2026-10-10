package com.dcim.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PeriodRow {
    private LocalDate period;
    private Long recordCount;
    private BigDecimal totalAmount;

    
    public LocalDate getPeriod() {
        return this.period;
    }

    
    public Long getRecordCount() {
        return this.recordCount;
    }

    
    public BigDecimal getTotalAmount() {
        return this.totalAmount;
    }

    
    public void setPeriod(final LocalDate period) {
        this.period = period;
    }

    
    public void setRecordCount(final Long recordCount) {
        this.recordCount = recordCount;
    }

    
    public void setTotalAmount(final BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof PeriodRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$recordCount = this.getRecordCount();
            Object other$recordCount = other.getRecordCount();
            if (this$recordCount == null ? other$recordCount == null : this$recordCount.equals(other$recordCount)) {
                Object this$period = this.getPeriod();
                Object other$period = other.getPeriod();
                if (this$period == null ? other$period == null : this$period.equals(other$period)) {
                    Object this$totalAmount = this.getTotalAmount();
                    Object other$totalAmount = other.getTotalAmount();
                    return this$totalAmount == null ? other$totalAmount == null : this$totalAmount.equals(other$totalAmount);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof PeriodRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $recordCount = this.getRecordCount();
        result = result * 59 + ($recordCount == null ? 43 : $recordCount.hashCode());
        Object $period = this.getPeriod();
        result = result * 59 + ($period == null ? 43 : $period.hashCode());
        Object $totalAmount = this.getTotalAmount();
        return result * 59 + ($totalAmount == null ? 43 : $totalAmount.hashCode());
    }

    
    @Override
    public String toString() {
        return "PeriodRow(period=" + this.getPeriod() + ", recordCount=" + this.getRecordCount() + ", totalAmount=" + this.getTotalAmount() + ")";
    }
}
