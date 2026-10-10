package com.dcim.rfid.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rfid_scan_record")
public class RfidScanRecordEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String batchNo;
    private String epc;
    private String assetNo;
    private String materialCode;
    private String sn;
    private String scanStatus;
    private String exceptionType;
    private String exceptionNote;
    private String operator;
    private LocalDateTime scanTime;

    
    public Long getId() {
        return this.id;
    }

    
    public String getBatchNo() {
        return this.batchNo;
    }

    
    public String getEpc() {
        return this.epc;
    }

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public String getMaterialCode() {
        return this.materialCode;
    }

    
    public String getSn() {
        return this.sn;
    }

    
    public String getScanStatus() {
        return this.scanStatus;
    }

    
    public String getExceptionType() {
        return this.exceptionType;
    }

    
    public String getExceptionNote() {
        return this.exceptionNote;
    }

    
    public String getOperator() {
        return this.operator;
    }

    
    public LocalDateTime getScanTime() {
        return this.scanTime;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setBatchNo(final String batchNo) {
        this.batchNo = batchNo;
    }

    
    public void setEpc(final String epc) {
        this.epc = epc;
    }

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
    }

    
    public void setMaterialCode(final String materialCode) {
        this.materialCode = materialCode;
    }

    
    public void setSn(final String sn) {
        this.sn = sn;
    }

    
    public void setScanStatus(final String scanStatus) {
        this.scanStatus = scanStatus;
    }

    
    public void setExceptionType(final String exceptionType) {
        this.exceptionType = exceptionType;
    }

    
    public void setExceptionNote(final String exceptionNote) {
        this.exceptionNote = exceptionNote;
    }

    
    public void setOperator(final String operator) {
        this.operator = operator;
    }

    
    public void setScanTime(final LocalDateTime scanTime) {
        this.scanTime = scanTime;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof RfidScanRecordEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$batchNo = this.getBatchNo();
                Object other$batchNo = other.getBatchNo();
                if (this$batchNo == null ? other$batchNo == null : this$batchNo.equals(other$batchNo)) {
                    Object this$epc = this.getEpc();
                    Object other$epc = other.getEpc();
                    if (this$epc == null ? other$epc == null : this$epc.equals(other$epc)) {
                        Object this$assetNo = this.getAssetNo();
                        Object other$assetNo = other.getAssetNo();
                        if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                            Object this$materialCode = this.getMaterialCode();
                            Object other$materialCode = other.getMaterialCode();
                            if (this$materialCode == null ? other$materialCode == null : this$materialCode.equals(other$materialCode)) {
                                Object this$sn = this.getSn();
                                Object other$sn = other.getSn();
                                if (this$sn == null ? other$sn == null : this$sn.equals(other$sn)) {
                                    Object this$scanStatus = this.getScanStatus();
                                    Object other$scanStatus = other.getScanStatus();
                                    if (this$scanStatus == null ? other$scanStatus == null : this$scanStatus.equals(other$scanStatus)) {
                                        Object this$exceptionType = this.getExceptionType();
                                        Object other$exceptionType = other.getExceptionType();
                                        if (this$exceptionType == null ? other$exceptionType == null : this$exceptionType.equals(other$exceptionType)) {
                                            Object this$exceptionNote = this.getExceptionNote();
                                            Object other$exceptionNote = other.getExceptionNote();
                                            if (this$exceptionNote == null ? other$exceptionNote == null : this$exceptionNote.equals(other$exceptionNote)) {
                                                Object this$operator = this.getOperator();
                                                Object other$operator = other.getOperator();
                                                if (this$operator == null ? other$operator == null : this$operator.equals(other$operator)) {
                                                    Object this$scanTime = this.getScanTime();
                                                    Object other$scanTime = other.getScanTime();
                                                    return this$scanTime == null ? other$scanTime == null : this$scanTime.equals(other$scanTime);
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
        return other instanceof RfidScanRecordEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $batchNo = this.getBatchNo();
        result = result * 59 + ($batchNo == null ? 43 : $batchNo.hashCode());
        Object $epc = this.getEpc();
        result = result * 59 + ($epc == null ? 43 : $epc.hashCode());
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $materialCode = this.getMaterialCode();
        result = result * 59 + ($materialCode == null ? 43 : $materialCode.hashCode());
        Object $sn = this.getSn();
        result = result * 59 + ($sn == null ? 43 : $sn.hashCode());
        Object $scanStatus = this.getScanStatus();
        result = result * 59 + ($scanStatus == null ? 43 : $scanStatus.hashCode());
        Object $exceptionType = this.getExceptionType();
        result = result * 59 + ($exceptionType == null ? 43 : $exceptionType.hashCode());
        Object $exceptionNote = this.getExceptionNote();
        result = result * 59 + ($exceptionNote == null ? 43 : $exceptionNote.hashCode());
        Object $operator = this.getOperator();
        result = result * 59 + ($operator == null ? 43 : $operator.hashCode());
        Object $scanTime = this.getScanTime();
        return result * 59 + ($scanTime == null ? 43 : $scanTime.hashCode());
    }

    
    @Override
    public String toString() {
        return "RfidScanRecordEntity(id="
            + this.getId()
            + ", batchNo="
            + this.getBatchNo()
            + ", epc="
            + this.getEpc()
            + ", assetNo="
            + this.getAssetNo()
            + ", materialCode="
            + this.getMaterialCode()
            + ", sn="
            + this.getSn()
            + ", scanStatus="
            + this.getScanStatus()
            + ", exceptionType="
            + this.getExceptionType()
            + ", exceptionNote="
            + this.getExceptionNote()
            + ", operator="
            + this.getOperator()
            + ", scanTime="
            + this.getScanTime()
            + ")";
    }
}
