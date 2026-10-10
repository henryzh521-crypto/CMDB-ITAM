package com.dcim.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("depreciation_record")
public class DepreciationRecordEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String assetNo;
    private LocalDate period;
    private BigDecimal originValue;
    private BigDecimal monthDep;
    private BigDecimal accumulated;
    private BigDecimal netValue;
    private String method;
    private LocalDateTime createdAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public LocalDate getPeriod() {
        return this.period;
    }

    
    public BigDecimal getOriginValue() {
        return this.originValue;
    }

    
    public BigDecimal getMonthDep() {
        return this.monthDep;
    }

    
    public BigDecimal getAccumulated() {
        return this.accumulated;
    }

    
    public BigDecimal getNetValue() {
        return this.netValue;
    }

    
    public String getMethod() {
        return this.method;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
    }

    
    public void setPeriod(final LocalDate period) {
        this.period = period;
    }

    
    public void setOriginValue(final BigDecimal originValue) {
        this.originValue = originValue;
    }

    
    public void setMonthDep(final BigDecimal monthDep) {
        this.monthDep = monthDep;
    }

    
    public void setAccumulated(final BigDecimal accumulated) {
        this.accumulated = accumulated;
    }

    
    public void setNetValue(final BigDecimal netValue) {
        this.netValue = netValue;
    }

    
    public void setMethod(final String method) {
        this.method = method;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof DepreciationRecordEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$assetNo = this.getAssetNo();
                Object other$assetNo = other.getAssetNo();
                if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                    Object this$period = this.getPeriod();
                    Object other$period = other.getPeriod();
                    if (this$period == null ? other$period == null : this$period.equals(other$period)) {
                        Object this$originValue = this.getOriginValue();
                        Object other$originValue = other.getOriginValue();
                        if (this$originValue == null ? other$originValue == null : this$originValue.equals(other$originValue)) {
                            Object this$monthDep = this.getMonthDep();
                            Object other$monthDep = other.getMonthDep();
                            if (this$monthDep == null ? other$monthDep == null : this$monthDep.equals(other$monthDep)) {
                                Object this$accumulated = this.getAccumulated();
                                Object other$accumulated = other.getAccumulated();
                                if (this$accumulated == null ? other$accumulated == null : this$accumulated.equals(other$accumulated)) {
                                    Object this$netValue = this.getNetValue();
                                    Object other$netValue = other.getNetValue();
                                    if (this$netValue == null ? other$netValue == null : this$netValue.equals(other$netValue)) {
                                        Object this$method = this.getMethod();
                                        Object other$method = other.getMethod();
                                        if (this$method == null ? other$method == null : this$method.equals(other$method)) {
                                            Object this$createdAt = this.getCreatedAt();
                                            Object other$createdAt = other.getCreatedAt();
                                            return this$createdAt == null ? other$createdAt == null : this$createdAt.equals(other$createdAt);
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
        return other instanceof DepreciationRecordEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $period = this.getPeriod();
        result = result * 59 + ($period == null ? 43 : $period.hashCode());
        Object $originValue = this.getOriginValue();
        result = result * 59 + ($originValue == null ? 43 : $originValue.hashCode());
        Object $monthDep = this.getMonthDep();
        result = result * 59 + ($monthDep == null ? 43 : $monthDep.hashCode());
        Object $accumulated = this.getAccumulated();
        result = result * 59 + ($accumulated == null ? 43 : $accumulated.hashCode());
        Object $netValue = this.getNetValue();
        result = result * 59 + ($netValue == null ? 43 : $netValue.hashCode());
        Object $method = this.getMethod();
        result = result * 59 + ($method == null ? 43 : $method.hashCode());
        Object $createdAt = this.getCreatedAt();
        return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "DepreciationRecordEntity(id="
            + this.getId()
            + ", assetNo="
            + this.getAssetNo()
            + ", period="
            + this.getPeriod()
            + ", originValue="
            + this.getOriginValue()
            + ", monthDep="
            + this.getMonthDep()
            + ", accumulated="
            + this.getAccumulated()
            + ", netValue="
            + this.getNetValue()
            + ", method="
            + this.getMethod()
            + ", createdAt="
            + this.getCreatedAt()
            + ")";
    }
}
