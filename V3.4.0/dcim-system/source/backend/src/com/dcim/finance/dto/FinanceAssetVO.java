package com.dcim.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FinanceAssetVO {
    private String assetNo;
    private String assetName;
    private String categoryCode;
    private String statusBiz;
    private String statusFin;
    private Boolean leaseFlag;
    private BigDecimal originValue;
    private BigDecimal residualRate;
    private Integer lifeMonths;
    private String depMethod;
    private LocalDate depStartDate;
    private BigDecimal accumulatedDep;
    private BigDecimal netValue;
    private BigDecimal impairment;

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public String getAssetName() {
        return this.assetName;
    }

    
    public String getCategoryCode() {
        return this.categoryCode;
    }

    
    public String getStatusBiz() {
        return this.statusBiz;
    }

    
    public String getStatusFin() {
        return this.statusFin;
    }

    
    public Boolean getLeaseFlag() {
        return this.leaseFlag;
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

    
    public String getDepMethod() {
        return this.depMethod;
    }

    
    public LocalDate getDepStartDate() {
        return this.depStartDate;
    }

    
    public BigDecimal getAccumulatedDep() {
        return this.accumulatedDep;
    }

    
    public BigDecimal getNetValue() {
        return this.netValue;
    }

    
    public BigDecimal getImpairment() {
        return this.impairment;
    }

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
    }

    
    public void setAssetName(final String assetName) {
        this.assetName = assetName;
    }

    
    public void setCategoryCode(final String categoryCode) {
        this.categoryCode = categoryCode;
    }

    
    public void setStatusBiz(final String statusBiz) {
        this.statusBiz = statusBiz;
    }

    
    public void setStatusFin(final String statusFin) {
        this.statusFin = statusFin;
    }

    
    public void setLeaseFlag(final Boolean leaseFlag) {
        this.leaseFlag = leaseFlag;
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

    
    public void setDepMethod(final String depMethod) {
        this.depMethod = depMethod;
    }

    
    public void setDepStartDate(final LocalDate depStartDate) {
        this.depStartDate = depStartDate;
    }

    
    public void setAccumulatedDep(final BigDecimal accumulatedDep) {
        this.accumulatedDep = accumulatedDep;
    }

    
    public void setNetValue(final BigDecimal netValue) {
        this.netValue = netValue;
    }

    
    public void setImpairment(final BigDecimal impairment) {
        this.impairment = impairment;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof FinanceAssetVO other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$leaseFlag = this.getLeaseFlag();
            Object other$leaseFlag = other.getLeaseFlag();
            if (this$leaseFlag == null ? other$leaseFlag == null : this$leaseFlag.equals(other$leaseFlag)) {
                Object this$lifeMonths = this.getLifeMonths();
                Object other$lifeMonths = other.getLifeMonths();
                if (this$lifeMonths == null ? other$lifeMonths == null : this$lifeMonths.equals(other$lifeMonths)) {
                    Object this$assetNo = this.getAssetNo();
                    Object other$assetNo = other.getAssetNo();
                    if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                        Object this$assetName = this.getAssetName();
                        Object other$assetName = other.getAssetName();
                        if (this$assetName == null ? other$assetName == null : this$assetName.equals(other$assetName)) {
                            Object this$categoryCode = this.getCategoryCode();
                            Object other$categoryCode = other.getCategoryCode();
                            if (this$categoryCode == null ? other$categoryCode == null : this$categoryCode.equals(other$categoryCode)) {
                                Object this$statusBiz = this.getStatusBiz();
                                Object other$statusBiz = other.getStatusBiz();
                                if (this$statusBiz == null ? other$statusBiz == null : this$statusBiz.equals(other$statusBiz)) {
                                    Object this$statusFin = this.getStatusFin();
                                    Object other$statusFin = other.getStatusFin();
                                    if (this$statusFin == null ? other$statusFin == null : this$statusFin.equals(other$statusFin)) {
                                        Object this$originValue = this.getOriginValue();
                                        Object other$originValue = other.getOriginValue();
                                        if (this$originValue == null ? other$originValue == null : this$originValue.equals(other$originValue)) {
                                            Object this$residualRate = this.getResidualRate();
                                            Object other$residualRate = other.getResidualRate();
                                            if (this$residualRate == null ? other$residualRate == null : this$residualRate.equals(other$residualRate)) {
                                                Object this$depMethod = this.getDepMethod();
                                                Object other$depMethod = other.getDepMethod();
                                                if (this$depMethod == null ? other$depMethod == null : this$depMethod.equals(other$depMethod)) {
                                                    Object this$depStartDate = this.getDepStartDate();
                                                    Object other$depStartDate = other.getDepStartDate();
                                                    if (this$depStartDate == null ? other$depStartDate == null : this$depStartDate.equals(other$depStartDate)) {
                                                        Object this$accumulatedDep = this.getAccumulatedDep();
                                                        Object other$accumulatedDep = other.getAccumulatedDep();
                                                        if (this$accumulatedDep == null
                                                            ? other$accumulatedDep == null
                                                            : this$accumulatedDep.equals(other$accumulatedDep)) {
                                                            Object this$netValue = this.getNetValue();
                                                            Object other$netValue = other.getNetValue();
                                                            if (this$netValue == null ? other$netValue == null : this$netValue.equals(other$netValue)) {
                                                                Object this$impairment = this.getImpairment();
                                                                Object other$impairment = other.getImpairment();
                                                                return this$impairment == null
                                                                    ? other$impairment == null
                                                                    : this$impairment.equals(other$impairment);
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
        return other instanceof FinanceAssetVO;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $leaseFlag = this.getLeaseFlag();
        result = result * 59 + ($leaseFlag == null ? 43 : $leaseFlag.hashCode());
        Object $lifeMonths = this.getLifeMonths();
        result = result * 59 + ($lifeMonths == null ? 43 : $lifeMonths.hashCode());
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $assetName = this.getAssetName();
        result = result * 59 + ($assetName == null ? 43 : $assetName.hashCode());
        Object $categoryCode = this.getCategoryCode();
        result = result * 59 + ($categoryCode == null ? 43 : $categoryCode.hashCode());
        Object $statusBiz = this.getStatusBiz();
        result = result * 59 + ($statusBiz == null ? 43 : $statusBiz.hashCode());
        Object $statusFin = this.getStatusFin();
        result = result * 59 + ($statusFin == null ? 43 : $statusFin.hashCode());
        Object $originValue = this.getOriginValue();
        result = result * 59 + ($originValue == null ? 43 : $originValue.hashCode());
        Object $residualRate = this.getResidualRate();
        result = result * 59 + ($residualRate == null ? 43 : $residualRate.hashCode());
        Object $depMethod = this.getDepMethod();
        result = result * 59 + ($depMethod == null ? 43 : $depMethod.hashCode());
        Object $depStartDate = this.getDepStartDate();
        result = result * 59 + ($depStartDate == null ? 43 : $depStartDate.hashCode());
        Object $accumulatedDep = this.getAccumulatedDep();
        result = result * 59 + ($accumulatedDep == null ? 43 : $accumulatedDep.hashCode());
        Object $netValue = this.getNetValue();
        result = result * 59 + ($netValue == null ? 43 : $netValue.hashCode());
        Object $impairment = this.getImpairment();
        return result * 59 + ($impairment == null ? 43 : $impairment.hashCode());
    }

    
    @Override
    public String toString() {
        return "FinanceAssetVO(assetNo="
            + this.getAssetNo()
            + ", assetName="
            + this.getAssetName()
            + ", categoryCode="
            + this.getCategoryCode()
            + ", statusBiz="
            + this.getStatusBiz()
            + ", statusFin="
            + this.getStatusFin()
            + ", leaseFlag="
            + this.getLeaseFlag()
            + ", originValue="
            + this.getOriginValue()
            + ", residualRate="
            + this.getResidualRate()
            + ", lifeMonths="
            + this.getLifeMonths()
            + ", depMethod="
            + this.getDepMethod()
            + ", depStartDate="
            + this.getDepStartDate()
            + ", accumulatedDep="
            + this.getAccumulatedDep()
            + ", netValue="
            + this.getNetValue()
            + ", impairment="
            + this.getImpairment()
            + ")";
    }
}
