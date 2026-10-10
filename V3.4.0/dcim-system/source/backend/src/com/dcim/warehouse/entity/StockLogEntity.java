package com.dcim.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("stock_log")
public class StockLogEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String logNo;
    private String logType;
    private String materialCode;
    private Integer qty;
    private LocalDate bizDate;
    private String operator;
    private String department;
    private String refWo;
    private String warehouseCode;
    private String fromWarehouseCode;
    private String remark;
    private LocalDateTime createdAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getLogNo() {
        return this.logNo;
    }

    
    public String getLogType() {
        return this.logType;
    }

    
    public String getMaterialCode() {
        return this.materialCode;
    }

    
    public Integer getQty() {
        return this.qty;
    }

    
    public LocalDate getBizDate() {
        return this.bizDate;
    }

    
    public String getOperator() {
        return this.operator;
    }

    
    public String getDepartment() {
        return this.department;
    }

    
    public String getRefWo() {
        return this.refWo;
    }

    
    public String getWarehouseCode() {
        return this.warehouseCode;
    }

    
    public String getFromWarehouseCode() {
        return this.fromWarehouseCode;
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

    
    public void setLogNo(final String logNo) {
        this.logNo = logNo;
    }

    
    public void setLogType(final String logType) {
        this.logType = logType;
    }

    
    public void setMaterialCode(final String materialCode) {
        this.materialCode = materialCode;
    }

    
    public void setQty(final Integer qty) {
        this.qty = qty;
    }

    
    public void setBizDate(final LocalDate bizDate) {
        this.bizDate = bizDate;
    }

    
    public void setOperator(final String operator) {
        this.operator = operator;
    }

    
    public void setDepartment(final String department) {
        this.department = department;
    }

    
    public void setRefWo(final String refWo) {
        this.refWo = refWo;
    }

    
    public void setWarehouseCode(final String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    
    public void setFromWarehouseCode(final String fromWarehouseCode) {
        this.fromWarehouseCode = fromWarehouseCode;
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
        } else if (!(o instanceof StockLogEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$qty = this.getQty();
                Object other$qty = other.getQty();
                if (this$qty == null ? other$qty == null : this$qty.equals(other$qty)) {
                    Object this$logNo = this.getLogNo();
                    Object other$logNo = other.getLogNo();
                    if (this$logNo == null ? other$logNo == null : this$logNo.equals(other$logNo)) {
                        Object this$logType = this.getLogType();
                        Object other$logType = other.getLogType();
                        if (this$logType == null ? other$logType == null : this$logType.equals(other$logType)) {
                            Object this$materialCode = this.getMaterialCode();
                            Object other$materialCode = other.getMaterialCode();
                            if (this$materialCode == null ? other$materialCode == null : this$materialCode.equals(other$materialCode)) {
                                Object this$bizDate = this.getBizDate();
                                Object other$bizDate = other.getBizDate();
                                if (this$bizDate == null ? other$bizDate == null : this$bizDate.equals(other$bizDate)) {
                                    Object this$operator = this.getOperator();
                                    Object other$operator = other.getOperator();
                                    if (this$operator == null ? other$operator == null : this$operator.equals(other$operator)) {
                                        Object this$department = this.getDepartment();
                                        Object other$department = other.getDepartment();
                                        if (this$department == null ? other$department == null : this$department.equals(other$department)) {
                                            Object this$refWo = this.getRefWo();
                                            Object other$refWo = other.getRefWo();
                                            if (this$refWo == null ? other$refWo == null : this$refWo.equals(other$refWo)) {
                                                Object this$warehouseCode = this.getWarehouseCode();
                                                Object other$warehouseCode = other.getWarehouseCode();
                                                if (this$warehouseCode == null ? other$warehouseCode == null : this$warehouseCode.equals(other$warehouseCode)) {
                                                    Object this$fromWarehouseCode = this.getFromWarehouseCode();
                                                    Object other$fromWarehouseCode = other.getFromWarehouseCode();
                                                    if (this$fromWarehouseCode == null
                                                        ? other$fromWarehouseCode == null
                                                        : this$fromWarehouseCode.equals(other$fromWarehouseCode)) {
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
            } else {
                return false;
            }
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof StockLogEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $qty = this.getQty();
        result = result * 59 + ($qty == null ? 43 : $qty.hashCode());
        Object $logNo = this.getLogNo();
        result = result * 59 + ($logNo == null ? 43 : $logNo.hashCode());
        Object $logType = this.getLogType();
        result = result * 59 + ($logType == null ? 43 : $logType.hashCode());
        Object $materialCode = this.getMaterialCode();
        result = result * 59 + ($materialCode == null ? 43 : $materialCode.hashCode());
        Object $bizDate = this.getBizDate();
        result = result * 59 + ($bizDate == null ? 43 : $bizDate.hashCode());
        Object $operator = this.getOperator();
        result = result * 59 + ($operator == null ? 43 : $operator.hashCode());
        Object $department = this.getDepartment();
        result = result * 59 + ($department == null ? 43 : $department.hashCode());
        Object $refWo = this.getRefWo();
        result = result * 59 + ($refWo == null ? 43 : $refWo.hashCode());
        Object $warehouseCode = this.getWarehouseCode();
        result = result * 59 + ($warehouseCode == null ? 43 : $warehouseCode.hashCode());
        Object $fromWarehouseCode = this.getFromWarehouseCode();
        result = result * 59 + ($fromWarehouseCode == null ? 43 : $fromWarehouseCode.hashCode());
        Object $remark = this.getRemark();
        result = result * 59 + ($remark == null ? 43 : $remark.hashCode());
        Object $createdAt = this.getCreatedAt();
        return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "StockLogEntity(id="
            + this.getId()
            + ", logNo="
            + this.getLogNo()
            + ", logType="
            + this.getLogType()
            + ", materialCode="
            + this.getMaterialCode()
            + ", qty="
            + this.getQty()
            + ", bizDate="
            + this.getBizDate()
            + ", operator="
            + this.getOperator()
            + ", department="
            + this.getDepartment()
            + ", refWo="
            + this.getRefWo()
            + ", warehouseCode="
            + this.getWarehouseCode()
            + ", fromWarehouseCode="
            + this.getFromWarehouseCode()
            + ", remark="
            + this.getRemark()
            + ", createdAt="
            + this.getCreatedAt()
            + ")";
    }
}
