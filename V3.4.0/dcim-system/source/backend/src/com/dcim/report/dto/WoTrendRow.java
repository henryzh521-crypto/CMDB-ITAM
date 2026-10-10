package com.dcim.report.dto;

import java.math.BigDecimal;

public class WoTrendRow {
    private String bucket;
    private Integer count;
    private Integer doneCount;
    private BigDecimal avgHours;

    
    public String getBucket() {
        return this.bucket;
    }

    
    public Integer getCount() {
        return this.count;
    }

    
    public Integer getDoneCount() {
        return this.doneCount;
    }

    
    public BigDecimal getAvgHours() {
        return this.avgHours;
    }

    
    public void setBucket(final String bucket) {
        this.bucket = bucket;
    }

    
    public void setCount(final Integer count) {
        this.count = count;
    }

    
    public void setDoneCount(final Integer doneCount) {
        this.doneCount = doneCount;
    }

    
    public void setAvgHours(final BigDecimal avgHours) {
        this.avgHours = avgHours;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof WoTrendRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$count = this.getCount();
            Object other$count = other.getCount();
            if (this$count == null ? other$count == null : this$count.equals(other$count)) {
                Object this$doneCount = this.getDoneCount();
                Object other$doneCount = other.getDoneCount();
                if (this$doneCount == null ? other$doneCount == null : this$doneCount.equals(other$doneCount)) {
                    Object this$bucket = this.getBucket();
                    Object other$bucket = other.getBucket();
                    if (this$bucket == null ? other$bucket == null : this$bucket.equals(other$bucket)) {
                        Object this$avgHours = this.getAvgHours();
                        Object other$avgHours = other.getAvgHours();
                        return this$avgHours == null ? other$avgHours == null : this$avgHours.equals(other$avgHours);
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
        return other instanceof WoTrendRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $count = this.getCount();
        result = result * 59 + ($count == null ? 43 : $count.hashCode());
        Object $doneCount = this.getDoneCount();
        result = result * 59 + ($doneCount == null ? 43 : $doneCount.hashCode());
        Object $bucket = this.getBucket();
        result = result * 59 + ($bucket == null ? 43 : $bucket.hashCode());
        Object $avgHours = this.getAvgHours();
        return result * 59 + ($avgHours == null ? 43 : $avgHours.hashCode());
    }

    
    @Override
    public String toString() {
        return "WoTrendRow(bucket="
            + this.getBucket()
            + ", count="
            + this.getCount()
            + ", doneCount="
            + this.getDoneCount()
            + ", avgHours="
            + this.getAvgHours()
            + ")";
    }
}
