package com.dcim.report.dto;

import java.math.BigDecimal;

public class WoDurationRow {
    private Long woCount;
    private Long doneCount;
    private Long rootCauseCount;
    private Long sampleCount;
    private BigDecimal avgHours;
    private BigDecimal medianHours;
    private BigDecimal p90Hours;
    private BigDecimal minHours;
    private BigDecimal maxHours;
    private Long repairSampleCount;
    private BigDecimal avgRepairHours;
    private BigDecimal medianRepairHours;
    private Long responseSampleCount;
    private BigDecimal avgResponseHours;
    private BigDecimal medianResponseHours;

    
    public Long getWoCount() {
        return this.woCount;
    }

    
    public Long getDoneCount() {
        return this.doneCount;
    }

    
    public Long getRootCauseCount() {
        return this.rootCauseCount;
    }

    
    public Long getSampleCount() {
        return this.sampleCount;
    }

    
    public BigDecimal getAvgHours() {
        return this.avgHours;
    }

    
    public BigDecimal getMedianHours() {
        return this.medianHours;
    }

    
    public BigDecimal getP90Hours() {
        return this.p90Hours;
    }

    
    public BigDecimal getMinHours() {
        return this.minHours;
    }

    
    public BigDecimal getMaxHours() {
        return this.maxHours;
    }

    
    public Long getRepairSampleCount() {
        return this.repairSampleCount;
    }

    
    public BigDecimal getAvgRepairHours() {
        return this.avgRepairHours;
    }

    
    public BigDecimal getMedianRepairHours() {
        return this.medianRepairHours;
    }

    
    public Long getResponseSampleCount() {
        return this.responseSampleCount;
    }

    
    public BigDecimal getAvgResponseHours() {
        return this.avgResponseHours;
    }

    
    public BigDecimal getMedianResponseHours() {
        return this.medianResponseHours;
    }

    
    public void setWoCount(final Long woCount) {
        this.woCount = woCount;
    }

    
    public void setDoneCount(final Long doneCount) {
        this.doneCount = doneCount;
    }

    
    public void setRootCauseCount(final Long rootCauseCount) {
        this.rootCauseCount = rootCauseCount;
    }

    
    public void setSampleCount(final Long sampleCount) {
        this.sampleCount = sampleCount;
    }

    
    public void setAvgHours(final BigDecimal avgHours) {
        this.avgHours = avgHours;
    }

    
    public void setMedianHours(final BigDecimal medianHours) {
        this.medianHours = medianHours;
    }

    
    public void setP90Hours(final BigDecimal p90Hours) {
        this.p90Hours = p90Hours;
    }

    
    public void setMinHours(final BigDecimal minHours) {
        this.minHours = minHours;
    }

    
    public void setMaxHours(final BigDecimal maxHours) {
        this.maxHours = maxHours;
    }

    
    public void setRepairSampleCount(final Long repairSampleCount) {
        this.repairSampleCount = repairSampleCount;
    }

    
    public void setAvgRepairHours(final BigDecimal avgRepairHours) {
        this.avgRepairHours = avgRepairHours;
    }

    
    public void setMedianRepairHours(final BigDecimal medianRepairHours) {
        this.medianRepairHours = medianRepairHours;
    }

    
    public void setResponseSampleCount(final Long responseSampleCount) {
        this.responseSampleCount = responseSampleCount;
    }

    
    public void setAvgResponseHours(final BigDecimal avgResponseHours) {
        this.avgResponseHours = avgResponseHours;
    }

    
    public void setMedianResponseHours(final BigDecimal medianResponseHours) {
        this.medianResponseHours = medianResponseHours;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof WoDurationRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$woCount = this.getWoCount();
            Object other$woCount = other.getWoCount();
            if (this$woCount == null ? other$woCount == null : this$woCount.equals(other$woCount)) {
                Object this$doneCount = this.getDoneCount();
                Object other$doneCount = other.getDoneCount();
                if (this$doneCount == null ? other$doneCount == null : this$doneCount.equals(other$doneCount)) {
                    Object this$rootCauseCount = this.getRootCauseCount();
                    Object other$rootCauseCount = other.getRootCauseCount();
                    if (this$rootCauseCount == null ? other$rootCauseCount == null : this$rootCauseCount.equals(other$rootCauseCount)) {
                        Object this$sampleCount = this.getSampleCount();
                        Object other$sampleCount = other.getSampleCount();
                        if (this$sampleCount == null ? other$sampleCount == null : this$sampleCount.equals(other$sampleCount)) {
                            Object this$repairSampleCount = this.getRepairSampleCount();
                            Object other$repairSampleCount = other.getRepairSampleCount();
                            if (this$repairSampleCount == null ? other$repairSampleCount == null : this$repairSampleCount.equals(other$repairSampleCount)) {
                                Object this$responseSampleCount = this.getResponseSampleCount();
                                Object other$responseSampleCount = other.getResponseSampleCount();
                                if (this$responseSampleCount == null
                                    ? other$responseSampleCount == null
                                    : this$responseSampleCount.equals(other$responseSampleCount)) {
                                    Object this$avgHours = this.getAvgHours();
                                    Object other$avgHours = other.getAvgHours();
                                    if (this$avgHours == null ? other$avgHours == null : this$avgHours.equals(other$avgHours)) {
                                        Object this$medianHours = this.getMedianHours();
                                        Object other$medianHours = other.getMedianHours();
                                        if (this$medianHours == null ? other$medianHours == null : this$medianHours.equals(other$medianHours)) {
                                            Object this$p90Hours = this.getP90Hours();
                                            Object other$p90Hours = other.getP90Hours();
                                            if (this$p90Hours == null ? other$p90Hours == null : this$p90Hours.equals(other$p90Hours)) {
                                                Object this$minHours = this.getMinHours();
                                                Object other$minHours = other.getMinHours();
                                                if (this$minHours == null ? other$minHours == null : this$minHours.equals(other$minHours)) {
                                                    Object this$maxHours = this.getMaxHours();
                                                    Object other$maxHours = other.getMaxHours();
                                                    if (this$maxHours == null ? other$maxHours == null : this$maxHours.equals(other$maxHours)) {
                                                        Object this$avgRepairHours = this.getAvgRepairHours();
                                                        Object other$avgRepairHours = other.getAvgRepairHours();
                                                        if (this$avgRepairHours == null
                                                            ? other$avgRepairHours == null
                                                            : this$avgRepairHours.equals(other$avgRepairHours)) {
                                                            Object this$medianRepairHours = this.getMedianRepairHours();
                                                            Object other$medianRepairHours = other.getMedianRepairHours();
                                                            if (this$medianRepairHours == null
                                                                ? other$medianRepairHours == null
                                                                : this$medianRepairHours.equals(other$medianRepairHours)) {
                                                                Object this$avgResponseHours = this.getAvgResponseHours();
                                                                Object other$avgResponseHours = other.getAvgResponseHours();
                                                                if (this$avgResponseHours == null
                                                                    ? other$avgResponseHours == null
                                                                    : this$avgResponseHours.equals(other$avgResponseHours)) {
                                                                    Object this$medianResponseHours = this.getMedianResponseHours();
                                                                    Object other$medianResponseHours = other.getMedianResponseHours();
                                                                    return this$medianResponseHours == null
                                                                        ? other$medianResponseHours == null
                                                                        : this$medianResponseHours.equals(other$medianResponseHours);
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
        return other instanceof WoDurationRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $woCount = this.getWoCount();
        result = result * 59 + ($woCount == null ? 43 : $woCount.hashCode());
        Object $doneCount = this.getDoneCount();
        result = result * 59 + ($doneCount == null ? 43 : $doneCount.hashCode());
        Object $rootCauseCount = this.getRootCauseCount();
        result = result * 59 + ($rootCauseCount == null ? 43 : $rootCauseCount.hashCode());
        Object $sampleCount = this.getSampleCount();
        result = result * 59 + ($sampleCount == null ? 43 : $sampleCount.hashCode());
        Object $repairSampleCount = this.getRepairSampleCount();
        result = result * 59 + ($repairSampleCount == null ? 43 : $repairSampleCount.hashCode());
        Object $responseSampleCount = this.getResponseSampleCount();
        result = result * 59 + ($responseSampleCount == null ? 43 : $responseSampleCount.hashCode());
        Object $avgHours = this.getAvgHours();
        result = result * 59 + ($avgHours == null ? 43 : $avgHours.hashCode());
        Object $medianHours = this.getMedianHours();
        result = result * 59 + ($medianHours == null ? 43 : $medianHours.hashCode());
        Object $p90Hours = this.getP90Hours();
        result = result * 59 + ($p90Hours == null ? 43 : $p90Hours.hashCode());
        Object $minHours = this.getMinHours();
        result = result * 59 + ($minHours == null ? 43 : $minHours.hashCode());
        Object $maxHours = this.getMaxHours();
        result = result * 59 + ($maxHours == null ? 43 : $maxHours.hashCode());
        Object $avgRepairHours = this.getAvgRepairHours();
        result = result * 59 + ($avgRepairHours == null ? 43 : $avgRepairHours.hashCode());
        Object $medianRepairHours = this.getMedianRepairHours();
        result = result * 59 + ($medianRepairHours == null ? 43 : $medianRepairHours.hashCode());
        Object $avgResponseHours = this.getAvgResponseHours();
        result = result * 59 + ($avgResponseHours == null ? 43 : $avgResponseHours.hashCode());
        Object $medianResponseHours = this.getMedianResponseHours();
        return result * 59 + ($medianResponseHours == null ? 43 : $medianResponseHours.hashCode());
    }

    
    @Override
    public String toString() {
        return "WoDurationRow(woCount="
            + this.getWoCount()
            + ", doneCount="
            + this.getDoneCount()
            + ", rootCauseCount="
            + this.getRootCauseCount()
            + ", sampleCount="
            + this.getSampleCount()
            + ", avgHours="
            + this.getAvgHours()
            + ", medianHours="
            + this.getMedianHours()
            + ", p90Hours="
            + this.getP90Hours()
            + ", minHours="
            + this.getMinHours()
            + ", maxHours="
            + this.getMaxHours()
            + ", repairSampleCount="
            + this.getRepairSampleCount()
            + ", avgRepairHours="
            + this.getAvgRepairHours()
            + ", medianRepairHours="
            + this.getMedianRepairHours()
            + ", responseSampleCount="
            + this.getResponseSampleCount()
            + ", avgResponseHours="
            + this.getAvgResponseHours()
            + ", medianResponseHours="
            + this.getMedianResponseHours()
            + ")";
    }
}
