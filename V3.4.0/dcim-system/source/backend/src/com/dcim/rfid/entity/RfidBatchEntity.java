package com.dcim.rfid.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rfid_batch")
public class RfidBatchEntity {
    @TableId(
        type = IdType.INPUT
    )
    private String batchNo;
    private String name;
    private String supplierCode;
    private String sourceType;
    private Integer itemCount;
    private Integer scannedCount;
    private Integer confirmedCount;
    private String status;
    private String creator;
    private LocalDateTime confirmedAt;
    private String remark;
    private LocalDateTime createdAt;

    
    public String getBatchNo() {
        return this.batchNo;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getSupplierCode() {
        return this.supplierCode;
    }

    
    public String getSourceType() {
        return this.sourceType;
    }

    
    public Integer getItemCount() {
        return this.itemCount;
    }

    
    public Integer getScannedCount() {
        return this.scannedCount;
    }

    
    public Integer getConfirmedCount() {
        return this.confirmedCount;
    }

    
    public String getStatus() {
        return this.status;
    }

    
    public String getCreator() {
        return this.creator;
    }

    
    public LocalDateTime getConfirmedAt() {
        return this.confirmedAt;
    }

    
    public String getRemark() {
        return this.remark;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public void setBatchNo(final String batchNo) {
        this.batchNo = batchNo;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setSupplierCode(final String supplierCode) {
        this.supplierCode = supplierCode;
    }

    
    public void setSourceType(final String sourceType) {
        this.sourceType = sourceType;
    }

    
    public void setItemCount(final Integer itemCount) {
        this.itemCount = itemCount;
    }

    
    public void setScannedCount(final Integer scannedCount) {
        this.scannedCount = scannedCount;
    }

    
    public void setConfirmedCount(final Integer confirmedCount) {
        this.confirmedCount = confirmedCount;
    }

    
    public void setStatus(final String status) {
        this.status = status;
    }

    
    public void setCreator(final String creator) {
        this.creator = creator;
    }

    
    public void setConfirmedAt(final LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
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
        } else if (!(o instanceof RfidBatchEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$itemCount = this.getItemCount();
            Object other$itemCount = other.getItemCount();
            if (this$itemCount == null ? other$itemCount == null : this$itemCount.equals(other$itemCount)) {
                Object this$scannedCount = this.getScannedCount();
                Object other$scannedCount = other.getScannedCount();
                if (this$scannedCount == null ? other$scannedCount == null : this$scannedCount.equals(other$scannedCount)) {
                    Object this$confirmedCount = this.getConfirmedCount();
                    Object other$confirmedCount = other.getConfirmedCount();
                    if (this$confirmedCount == null ? other$confirmedCount == null : this$confirmedCount.equals(other$confirmedCount)) {
                        Object this$batchNo = this.getBatchNo();
                        Object other$batchNo = other.getBatchNo();
                        if (this$batchNo == null ? other$batchNo == null : this$batchNo.equals(other$batchNo)) {
                            Object this$name = this.getName();
                            Object other$name = other.getName();
                            if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                                Object this$supplierCode = this.getSupplierCode();
                                Object other$supplierCode = other.getSupplierCode();
                                if (this$supplierCode == null ? other$supplierCode == null : this$supplierCode.equals(other$supplierCode)) {
                                    Object this$sourceType = this.getSourceType();
                                    Object other$sourceType = other.getSourceType();
                                    if (this$sourceType == null ? other$sourceType == null : this$sourceType.equals(other$sourceType)) {
                                        Object this$status = this.getStatus();
                                        Object other$status = other.getStatus();
                                        if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                                            Object this$creator = this.getCreator();
                                            Object other$creator = other.getCreator();
                                            if (this$creator == null ? other$creator == null : this$creator.equals(other$creator)) {
                                                Object this$confirmedAt = this.getConfirmedAt();
                                                Object other$confirmedAt = other.getConfirmedAt();
                                                if (this$confirmedAt == null ? other$confirmedAt == null : this$confirmedAt.equals(other$confirmedAt)) {
                                                    Object this$remark = this.getRemark();
                                                    Object other$remark = other.getRemark();
                                                    if (this$remark == null ? other$remark == null : this$remark.equals(other$remark)) {
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
        return other instanceof RfidBatchEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $itemCount = this.getItemCount();
        result = result * 59 + ($itemCount == null ? 43 : $itemCount.hashCode());
        Object $scannedCount = this.getScannedCount();
        result = result * 59 + ($scannedCount == null ? 43 : $scannedCount.hashCode());
        Object $confirmedCount = this.getConfirmedCount();
        result = result * 59 + ($confirmedCount == null ? 43 : $confirmedCount.hashCode());
        Object $batchNo = this.getBatchNo();
        result = result * 59 + ($batchNo == null ? 43 : $batchNo.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $supplierCode = this.getSupplierCode();
        result = result * 59 + ($supplierCode == null ? 43 : $supplierCode.hashCode());
        Object $sourceType = this.getSourceType();
        result = result * 59 + ($sourceType == null ? 43 : $sourceType.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $creator = this.getCreator();
        result = result * 59 + ($creator == null ? 43 : $creator.hashCode());
        Object $confirmedAt = this.getConfirmedAt();
        result = result * 59 + ($confirmedAt == null ? 43 : $confirmedAt.hashCode());
        Object $remark = this.getRemark();
        result = result * 59 + ($remark == null ? 43 : $remark.hashCode());
        Object $createdAt = this.getCreatedAt();
        return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "RfidBatchEntity(batchNo="
            + this.getBatchNo()
            + ", name="
            + this.getName()
            + ", supplierCode="
            + this.getSupplierCode()
            + ", sourceType="
            + this.getSourceType()
            + ", itemCount="
            + this.getItemCount()
            + ", scannedCount="
            + this.getScannedCount()
            + ", confirmedCount="
            + this.getConfirmedCount()
            + ", status="
            + this.getStatus()
            + ", creator="
            + this.getCreator()
            + ", confirmedAt="
            + this.getConfirmedAt()
            + ", remark="
            + this.getRemark()
            + ", createdAt="
            + this.getCreatedAt()
            + ")";
    }
}
