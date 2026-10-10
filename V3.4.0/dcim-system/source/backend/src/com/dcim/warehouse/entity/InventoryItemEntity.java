package com.dcim.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.JsonbTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(
    value = "inventory_item",
    autoResultMap = true
)
public class InventoryItemEntity {
    @TableId(
        type = IdType.INPUT
    )
    private String itemNo;
    private String taskNo;
    private String assetNo;
    private String materialCode;
    private String warehouseCode;
    private Integer countedQty;
    @TableField(
        typeHandler = JsonbTypeHandler.class
    )
    private Map<String, Object> expected;
    private String result;
    private LocalDateTime checkedAt;
    private String checker;
    private String diffNote;

    
    public String getItemNo() {
        return this.itemNo;
    }

    
    public String getTaskNo() {
        return this.taskNo;
    }

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public String getMaterialCode() {
        return this.materialCode;
    }

    
    public String getWarehouseCode() {
        return this.warehouseCode;
    }

    
    public Integer getCountedQty() {
        return this.countedQty;
    }

    
    public Map<String, Object> getExpected() {
        return this.expected;
    }

    
    public String getResult() {
        return this.result;
    }

    
    public LocalDateTime getCheckedAt() {
        return this.checkedAt;
    }

    
    public String getChecker() {
        return this.checker;
    }

    
    public String getDiffNote() {
        return this.diffNote;
    }

    
    public void setItemNo(final String itemNo) {
        this.itemNo = itemNo;
    }

    
    public void setTaskNo(final String taskNo) {
        this.taskNo = taskNo;
    }

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
    }

    
    public void setMaterialCode(final String materialCode) {
        this.materialCode = materialCode;
    }

    
    public void setWarehouseCode(final String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    
    public void setCountedQty(final Integer countedQty) {
        this.countedQty = countedQty;
    }

    
    public void setExpected(final Map<String, Object> expected) {
        this.expected = expected;
    }

    
    public void setResult(final String result) {
        this.result = result;
    }

    
    public void setCheckedAt(final LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }

    
    public void setChecker(final String checker) {
        this.checker = checker;
    }

    
    public void setDiffNote(final String diffNote) {
        this.diffNote = diffNote;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof InventoryItemEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$countedQty = this.getCountedQty();
            Object other$countedQty = other.getCountedQty();
            if (this$countedQty == null ? other$countedQty == null : this$countedQty.equals(other$countedQty)) {
                Object this$itemNo = this.getItemNo();
                Object other$itemNo = other.getItemNo();
                if (this$itemNo == null ? other$itemNo == null : this$itemNo.equals(other$itemNo)) {
                    Object this$taskNo = this.getTaskNo();
                    Object other$taskNo = other.getTaskNo();
                    if (this$taskNo == null ? other$taskNo == null : this$taskNo.equals(other$taskNo)) {
                        Object this$assetNo = this.getAssetNo();
                        Object other$assetNo = other.getAssetNo();
                        if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                            Object this$materialCode = this.getMaterialCode();
                            Object other$materialCode = other.getMaterialCode();
                            if (this$materialCode == null ? other$materialCode == null : this$materialCode.equals(other$materialCode)) {
                                Object this$warehouseCode = this.getWarehouseCode();
                                Object other$warehouseCode = other.getWarehouseCode();
                                if (this$warehouseCode == null ? other$warehouseCode == null : this$warehouseCode.equals(other$warehouseCode)) {
                                    Object this$expected = this.getExpected();
                                    Object other$expected = other.getExpected();
                                    if (this$expected == null ? other$expected == null : this$expected.equals(other$expected)) {
                                        Object this$result = this.getResult();
                                        Object other$result = other.getResult();
                                        if (this$result == null ? other$result == null : this$result.equals(other$result)) {
                                            Object this$checkedAt = this.getCheckedAt();
                                            Object other$checkedAt = other.getCheckedAt();
                                            if (this$checkedAt == null ? other$checkedAt == null : this$checkedAt.equals(other$checkedAt)) {
                                                Object this$checker = this.getChecker();
                                                Object other$checker = other.getChecker();
                                                if (this$checker == null ? other$checker == null : this$checker.equals(other$checker)) {
                                                    Object this$diffNote = this.getDiffNote();
                                                    Object other$diffNote = other.getDiffNote();
                                                    return this$diffNote == null ? other$diffNote == null : this$diffNote.equals(other$diffNote);
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
        return other instanceof InventoryItemEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $countedQty = this.getCountedQty();
        result = result * 59 + ($countedQty == null ? 43 : $countedQty.hashCode());
        Object $itemNo = this.getItemNo();
        result = result * 59 + ($itemNo == null ? 43 : $itemNo.hashCode());
        Object $taskNo = this.getTaskNo();
        result = result * 59 + ($taskNo == null ? 43 : $taskNo.hashCode());
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $materialCode = this.getMaterialCode();
        result = result * 59 + ($materialCode == null ? 43 : $materialCode.hashCode());
        Object $warehouseCode = this.getWarehouseCode();
        result = result * 59 + ($warehouseCode == null ? 43 : $warehouseCode.hashCode());
        Object $expected = this.getExpected();
        result = result * 59 + ($expected == null ? 43 : $expected.hashCode());
        Object $result = this.getResult();
        result = result * 59 + ($result == null ? 43 : $result.hashCode());
        Object $checkedAt = this.getCheckedAt();
        result = result * 59 + ($checkedAt == null ? 43 : $checkedAt.hashCode());
        Object $checker = this.getChecker();
        result = result * 59 + ($checker == null ? 43 : $checker.hashCode());
        Object $diffNote = this.getDiffNote();
        return result * 59 + ($diffNote == null ? 43 : $diffNote.hashCode());
    }

    
    @Override
    public String toString() {
        return "InventoryItemEntity(itemNo="
            + this.getItemNo()
            + ", taskNo="
            + this.getTaskNo()
            + ", assetNo="
            + this.getAssetNo()
            + ", materialCode="
            + this.getMaterialCode()
            + ", warehouseCode="
            + this.getWarehouseCode()
            + ", countedQty="
            + this.getCountedQty()
            + ", expected="
            + this.getExpected()
            + ", result="
            + this.getResult()
            + ", checkedAt="
            + this.getCheckedAt()
            + ", checker="
            + this.getChecker()
            + ", diffNote="
            + this.getDiffNote()
            + ")";
    }
}
