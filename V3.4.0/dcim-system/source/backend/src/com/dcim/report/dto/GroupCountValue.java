package com.dcim.report.dto;

import java.math.BigDecimal;

public class GroupCountValue {
    private String key;
    private String label;
    private Long count;
    private BigDecimal totalValue;

    
    public String getKey() {
        return this.key;
    }

    
    public String getLabel() {
        return this.label;
    }

    
    public Long getCount() {
        return this.count;
    }

    
    public BigDecimal getTotalValue() {
        return this.totalValue;
    }

    
    public void setKey(final String key) {
        this.key = key;
    }

    
    public void setLabel(final String label) {
        this.label = label;
    }

    
    public void setCount(final Long count) {
        this.count = count;
    }

    
    public void setTotalValue(final BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof GroupCountValue other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$count = this.getCount();
            Object other$count = other.getCount();
            if (this$count == null ? other$count == null : this$count.equals(other$count)) {
                Object this$key = this.getKey();
                Object other$key = other.getKey();
                if (this$key == null ? other$key == null : this$key.equals(other$key)) {
                    Object this$label = this.getLabel();
                    Object other$label = other.getLabel();
                    if (this$label == null ? other$label == null : this$label.equals(other$label)) {
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
        return other instanceof GroupCountValue;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $count = this.getCount();
        result = result * 59 + ($count == null ? 43 : $count.hashCode());
        Object $key = this.getKey();
        result = result * 59 + ($key == null ? 43 : $key.hashCode());
        Object $label = this.getLabel();
        result = result * 59 + ($label == null ? 43 : $label.hashCode());
        Object $totalValue = this.getTotalValue();
        return result * 59 + ($totalValue == null ? 43 : $totalValue.hashCode());
    }

    
    @Override
    public String toString() {
        return "GroupCountValue(key="
            + this.getKey()
            + ", label="
            + this.getLabel()
            + ", count="
            + this.getCount()
            + ", totalValue="
            + this.getTotalValue()
            + ")";
    }
}
