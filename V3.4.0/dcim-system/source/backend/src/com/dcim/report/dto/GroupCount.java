package com.dcim.report.dto;


public class GroupCount {
    private String key;
    private String label;
    private Long count;

    
    public String getKey() {
        return this.key;
    }

    
    public String getLabel() {
        return this.label;
    }

    
    public Long getCount() {
        return this.count;
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

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof GroupCount other)) {
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
                    return this$label == null ? other$label == null : this$label.equals(other$label);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof GroupCount;
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
        return result * 59 + ($label == null ? 43 : $label.hashCode());
    }

    
    @Override
    public String toString() {
        return "GroupCount(key=" + this.getKey() + ", label=" + this.getLabel() + ", count=" + this.getCount() + ")";
    }
}
