package com.dcim.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.JsonListTypeHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@TableName(
    value = "contract",
    autoResultMap = true
)
public class ContractEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String contractNo;
    private String name;
    private String type;
    private String party;
    private BigDecimal amount;
    private LocalDate signDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal discountRate;
    private Boolean purchaseOption;
    @TableField(
        typeHandler = JsonListTypeHandler.class
    )
    private List<Object> paymentPlan;
    private String status;
    private String remark;
    private LocalDateTime createdAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getContractNo() {
        return this.contractNo;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getType() {
        return this.type;
    }

    
    public String getParty() {
        return this.party;
    }

    
    public BigDecimal getAmount() {
        return this.amount;
    }

    
    public LocalDate getSignDate() {
        return this.signDate;
    }

    
    public LocalDate getStartDate() {
        return this.startDate;
    }

    
    public LocalDate getEndDate() {
        return this.endDate;
    }

    
    public BigDecimal getDiscountRate() {
        return this.discountRate;
    }

    
    public Boolean getPurchaseOption() {
        return this.purchaseOption;
    }

    
    public List<Object> getPaymentPlan() {
        return this.paymentPlan;
    }

    
    public String getStatus() {
        return this.status;
    }

    
    public String getRemark() {
        return this.remark;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setContractNo(final String contractNo) {
        this.contractNo = contractNo;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setType(final String type) {
        this.type = type;
    }

    
    public void setParty(final String party) {
        this.party = party;
    }

    
    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    
    public void setSignDate(final LocalDate signDate) {
        this.signDate = signDate;
    }

    
    public void setStartDate(final LocalDate startDate) {
        this.startDate = startDate;
    }

    
    public void setEndDate(final LocalDate endDate) {
        this.endDate = endDate;
    }

    
    public void setDiscountRate(final BigDecimal discountRate) {
        this.discountRate = discountRate;
    }

    
    public void setPurchaseOption(final Boolean purchaseOption) {
        this.purchaseOption = purchaseOption;
    }

    
    public void setPaymentPlan(final List<Object> paymentPlan) {
        this.paymentPlan = paymentPlan;
    }

    
    public void setStatus(final String status) {
        this.status = status;
    }

    
    public void setRemark(final String remark) {
        this.remark = remark;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof ContractEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$purchaseOption = this.getPurchaseOption();
                Object other$purchaseOption = other.getPurchaseOption();
                if (this$purchaseOption == null ? other$purchaseOption == null : this$purchaseOption.equals(other$purchaseOption)) {
                    Object this$contractNo = this.getContractNo();
                    Object other$contractNo = other.getContractNo();
                    if (this$contractNo == null ? other$contractNo == null : this$contractNo.equals(other$contractNo)) {
                        Object this$name = this.getName();
                        Object other$name = other.getName();
                        if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                            Object this$type = this.getType();
                            Object other$type = other.getType();
                            if (this$type == null ? other$type == null : this$type.equals(other$type)) {
                                Object this$party = this.getParty();
                                Object other$party = other.getParty();
                                if (this$party == null ? other$party == null : this$party.equals(other$party)) {
                                    Object this$amount = this.getAmount();
                                    Object other$amount = other.getAmount();
                                    if (this$amount == null ? other$amount == null : this$amount.equals(other$amount)) {
                                        Object this$signDate = this.getSignDate();
                                        Object other$signDate = other.getSignDate();
                                        if (this$signDate == null ? other$signDate == null : this$signDate.equals(other$signDate)) {
                                            Object this$startDate = this.getStartDate();
                                            Object other$startDate = other.getStartDate();
                                            if (this$startDate == null ? other$startDate == null : this$startDate.equals(other$startDate)) {
                                                Object this$endDate = this.getEndDate();
                                                Object other$endDate = other.getEndDate();
                                                if (this$endDate == null ? other$endDate == null : this$endDate.equals(other$endDate)) {
                                                    Object this$discountRate = this.getDiscountRate();
                                                    Object other$discountRate = other.getDiscountRate();
                                                    if (this$discountRate == null ? other$discountRate == null : this$discountRate.equals(other$discountRate)) {
                                                        Object this$paymentPlan = this.getPaymentPlan();
                                                        Object other$paymentPlan = other.getPaymentPlan();
                                                        if (this$paymentPlan == null ? other$paymentPlan == null : this$paymentPlan.equals(other$paymentPlan)) {
                                                            Object this$status = this.getStatus();
                                                            Object other$status = other.getStatus();
                                                            if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                                                                Object this$remark = this.getRemark();
                                                                Object other$remark = other.getRemark();
                                                                if (this$remark == null ? other$remark == null : this$remark.equals(other$remark)) {
                                                                    Object this$createdAt = this.getCreatedAt();
                                                                    Object other$createdAt = other.getCreatedAt();
                                                                    return this$createdAt == null
                                                                        ? other$createdAt == null
                                                                        : this$createdAt.equals(other$createdAt);
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
        return other instanceof ContractEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $purchaseOption = this.getPurchaseOption();
        result = result * 59 + ($purchaseOption == null ? 43 : $purchaseOption.hashCode());
        Object $contractNo = this.getContractNo();
        result = result * 59 + ($contractNo == null ? 43 : $contractNo.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $type = this.getType();
        result = result * 59 + ($type == null ? 43 : $type.hashCode());
        Object $party = this.getParty();
        result = result * 59 + ($party == null ? 43 : $party.hashCode());
        Object $amount = this.getAmount();
        result = result * 59 + ($amount == null ? 43 : $amount.hashCode());
        Object $signDate = this.getSignDate();
        result = result * 59 + ($signDate == null ? 43 : $signDate.hashCode());
        Object $startDate = this.getStartDate();
        result = result * 59 + ($startDate == null ? 43 : $startDate.hashCode());
        Object $endDate = this.getEndDate();
        result = result * 59 + ($endDate == null ? 43 : $endDate.hashCode());
        Object $discountRate = this.getDiscountRate();
        result = result * 59 + ($discountRate == null ? 43 : $discountRate.hashCode());
        Object $paymentPlan = this.getPaymentPlan();
        result = result * 59 + ($paymentPlan == null ? 43 : $paymentPlan.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $remark = this.getRemark();
        result = result * 59 + ($remark == null ? 43 : $remark.hashCode());
        Object $createdAt = this.getCreatedAt();
        return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "ContractEntity(id="
            + this.getId()
            + ", contractNo="
            + this.getContractNo()
            + ", name="
            + this.getName()
            + ", type="
            + this.getType()
            + ", party="
            + this.getParty()
            + ", amount="
            + this.getAmount()
            + ", signDate="
            + this.getSignDate()
            + ", startDate="
            + this.getStartDate()
            + ", endDate="
            + this.getEndDate()
            + ", discountRate="
            + this.getDiscountRate()
            + ", purchaseOption="
            + this.getPurchaseOption()
            + ", paymentPlan="
            + this.getPaymentPlan()
            + ", status="
            + this.getStatus()
            + ", remark="
            + this.getRemark()
            + ", createdAt="
            + this.getCreatedAt()
            + ")";
    }
}
