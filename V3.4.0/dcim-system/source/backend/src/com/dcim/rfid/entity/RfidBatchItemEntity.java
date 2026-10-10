package com.dcim.rfid.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("rfid_batch_item")
public class RfidBatchItemEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String batchNo;
    private String materialCode;
    private Integer expectedQty;
    private Integer receivedQty;

    
    public Long getId() {
        return this.id;
    }

    
    public String getBatchNo() {
        return this.batchNo;
    }

    
    public String getMaterialCode() {
        return this.materialCode;
    }

    
    public Integer getExpectedQty() {
        return this.expectedQty;
    }

    
    public Integer getReceivedQty() {
        return this.receivedQty;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setBatchNo(final String batchNo) {
        this.batchNo = batchNo;
    }

    
    public void setMaterialCode(final String materialCode) {
        this.materialCode = materialCode;
    }

    
    public void setExpectedQty(final Integer expectedQty) {
        this.expectedQty = expectedQty;
    }

    
    public void setReceivedQty(final Integer receivedQty) {
        this.receivedQty = receivedQty;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof RfidBatchItemEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$expectedQty = this.getExpectedQty();
                Object other$expectedQty = other.getExpectedQty();
                if (this$expectedQty == null ? other$expectedQty == null : this$expectedQty.equals(other$expectedQty)) {
                    Object this$receivedQty = this.getReceivedQty();
                    Object other$receivedQty = other.getReceivedQty();
                    if (this$receivedQty == null ? other$receivedQty == null : this$receivedQty.equals(other$receivedQty)) {
                        Object this$batchNo = this.getBatchNo();
                        Object other$batchNo = other.getBatchNo();
                        if (this$batchNo == null ? other$batchNo == null : this$batchNo.equals(other$batchNo)) {
                            Object this$materialCode = this.getMaterialCode();
                            Object other$materialCode = other.getMaterialCode();
                            return this$materialCode == null ? other$materialCode == null : this$materialCode.equals(other$materialCode);
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
        return other instanceof RfidBatchItemEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $expectedQty = this.getExpectedQty();
        result = result * 59 + ($expectedQty == null ? 43 : $expectedQty.hashCode());
        Object $receivedQty = this.getReceivedQty();
        result = result * 59 + ($receivedQty == null ? 43 : $receivedQty.hashCode());
        Object $batchNo = this.getBatchNo();
        result = result * 59 + ($batchNo == null ? 43 : $batchNo.hashCode());
        Object $materialCode = this.getMaterialCode();
        return result * 59 + ($materialCode == null ? 43 : $materialCode.hashCode());
    }

    
    @Override
    public String toString() {
        return "RfidBatchItemEntity(id="
            + this.getId()
            + ", batchNo="
            + this.getBatchNo()
            + ", materialCode="
            + this.getMaterialCode()
            + ", expectedQty="
            + this.getExpectedQty()
            + ", receivedQty="
            + this.getReceivedQty()
            + ")";
    }
}
