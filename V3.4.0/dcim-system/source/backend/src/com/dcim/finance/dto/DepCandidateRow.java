package com.dcim.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DepCandidateRow {
    private String assetNo;
    private String statusFin;
    private BigDecimal originValue;
    private BigDecimal residualRate;
    private Integer lifeMonths;
    private LocalDate depStartDate;
    private BigDecimal accumulatedDep;
    private BigDecimal impairment;
    private Boolean leaseFlag;

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public String getStatusFin() {
        return this.statusFin;
    }

    
    public BigDecimal getOriginValue() {
        return this.originValue;
    }

    
    public BigDecimal getResidualRate() {
        return this.residualRate;
    }

    
    public Integer getLifeMonths() {
        return this.lifeMonths;
    }

    
    public LocalDate getDepStartDate() {
        return this.depStartDate;
    }

    
    public BigDecimal getAccumulatedDep() {
        return this.accumulatedDep;
    }

    
    public BigDecimal getImpairment() {
        return this.impairment;
    }

    
    public Boolean getLeaseFlag() {
        return this.leaseFlag;
    }

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
    }

    
    public void setStatusFin(final String statusFin) {
        this.statusFin = statusFin;
    }

    
    public void setOriginValue(final BigDecimal originValue) {
        this.originValue = originValue;
    }

    
    public void setResidualRate(final BigDecimal residualRate) {
        this.residualRate = residualRate;
    }

    
    public void setLifeMonths(final Integer lifeMonths) {
        this.lifeMonths = lifeMonths;
    }

    
    public void setDepStartDate(final LocalDate depStartDate) {
        this.depStartDate = depStartDate;
    }

    
    public void setAccumulatedDep(final BigDecimal accumulatedDep) {
        this.accumulatedDep = accumulatedDep;
    }

    
    public void setImpairment(final BigDecimal impairment) {
        this.impairment = impairment;
    }

    
    public void setLeaseFlag(final Boolean leaseFlag) {
        this.leaseFlag = leaseFlag;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof DepCandidateRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$lifeMonths = this.getLifeMonths();
            Object other$lifeMonths = other.getLifeMonths();
            if (this$lifeMonths == null ? other$lifeMonths == null : this$lifeMonths.equals(other$lifeMonths)) {
                Object this$leaseFlag = this.getLeaseFlag();
                Object other$leaseFlag = other.getLeaseFlag();
                if (this$leaseFlag == null ? other$leaseFlag == null : this$leaseFlag.equals(other$leaseFlag)) {
                    Object this$assetNo = this.getAssetNo();
                    Object other$assetNo = other.getAssetNo();
                    if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                        Object this$statusFin = this.getStatusFin();
                        Object other$statusFin = other.getStatusFin();
                        if (this$statusFin == null ? other$statusFin == null : this$statusFin.equals(other$statusFin)) {
                            Object this$originValue = this.getOriginValue();
                            Object other$originValue = other.getOriginValue();
                            if (this$originValue == null ? other$originValue == null : this$originValue.equals(other$originValue)) {
                                Object this$residualRate = this.getResidualRate();
                                Object other$residualRate = other.getResidualRate();
                                if (this$residualRate == null ? other$residualRate == null : this$residualRate.equals(other$residualRate)) {
                                    Object this$depStartDate = this.getDepStartDate();
                                    Object other$depStartDate = other.getDepStartDate();
                                    if (this$depStartDate == null ? other$depStartDate == null : this$depStartDate.equals(other$depStartDate)) {
                                        Object this$accumulatedDep = this.getAccumulatedDep();
                                        Object other$accumulatedDep = other.getAccumulatedDep();
                                        if (this$accumulatedDep == null ? other$accumulatedDep == null : this$accumulatedDep.equals(other$accumulatedDep)) {
                                            Object this$impairment = this.getImpairment();
                                            Object other$impairment = other.getImpairment();
                                            return this$impairment == null ? other$impairment == null : this$impairment.equals(other$impairment);
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
        return other instanceof DepCandidateRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $lifeMonths = this.getLifeMonths();
        result = result * 59 + ($lifeMonths == null ? 43 : $lifeMonths.hashCode());
        Object $leaseFlag = this.getLeaseFlag();
        result = result * 59 + ($leaseFlag == null ? 43 : $leaseFlag.hashCode());
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $statusFin = this.getStatusFin();
        result = result * 59 + ($statusFin == null ? 43 : $statusFin.hashCode());
        Object $originValue = this.getOriginValue();
        result = result * 59 + ($originValue == null ? 43 : $originValue.hashCode());
        Object $residualRate = this.getResidualRate();
        result = result * 59 + ($residualRate == null ? 43 : $residualRate.hashCode());
        Object $depStartDate = this.getDepStartDate();
        result = result * 59 + ($depStartDate == null ? 43 : $depStartDate.hashCode());
        Object $accumulatedDep = this.getAccumulatedDep();
        result = result * 59 + ($accumulatedDep == null ? 43 : $accumulatedDep.hashCode());
        Object $impairment = this.getImpairment();
        return result * 59 + ($impairment == null ? 43 : $impairment.hashCode());
    }

    
    @Override
    public String toString() {
        return "DepCandidateRow(assetNo="
            + this.getAssetNo()
            + ", statusFin="
            + this.getStatusFin()
            + ", originValue="
            + this.getOriginValue()
            + ", residualRate="
            + this.getResidualRate()
            + ", lifeMonths="
            + this.getLifeMonths()
            + ", depStartDate="
            + this.getDepStartDate()
            + ", accumulatedDep="
            + this.getAccumulatedDep()
            + ", impairment="
            + this.getImpairment()
            + ", leaseFlag="
            + this.getLeaseFlag()
            + ")";
    }
}
