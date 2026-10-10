package com.dcim.report.dto;

import java.math.BigDecimal;

public class MaterialConsumptionRow {
    private String materialCode;
    private String name;
    private String unit;
    private Integer qty;
    private BigDecimal value;
    private Integer woCount;

    
    public String getMaterialCode() {
        return this.materialCode;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getUnit() {
        return this.unit;
    }

    
    public Integer getQty() {
        return this.qty;
    }

    
    public BigDecimal getValue() {
        return this.value;
    }

    
    public Integer getWoCount() {
        return this.woCount;
    }

    
    public void setMaterialCode(final String materialCode) {
        this.materialCode = materialCode;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setUnit(final String unit) {
        this.unit = unit;
    }

    
    public void setQty(final Integer qty) {
        this.qty = qty;
    }

    
    public void setValue(final BigDecimal value) {
        this.value = value;
    }

    
    public void setWoCount(final Integer woCount) {
        this.woCount = woCount;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof MaterialConsumptionRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$qty = this.getQty();
            Object other$qty = other.getQty();
            if (this$qty == null ? other$qty == null : this$qty.equals(other$qty)) {
                Object this$woCount = this.getWoCount();
                Object other$woCount = other.getWoCount();
                if (this$woCount == null ? other$woCount == null : this$woCount.equals(other$woCount)) {
                    Object this$materialCode = this.getMaterialCode();
                    Object other$materialCode = other.getMaterialCode();
                    if (this$materialCode == null ? other$materialCode == null : this$materialCode.equals(other$materialCode)) {
                        Object this$name = this.getName();
                        Object other$name = other.getName();
                        if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                            Object this$unit = this.getUnit();
                            Object other$unit = other.getUnit();
                            if (this$unit == null ? other$unit == null : this$unit.equals(other$unit)) {
                                Object this$value = this.getValue();
                                Object other$value = other.getValue();
                                return this$value == null ? other$value == null : this$value.equals(other$value);
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
        return other instanceof MaterialConsumptionRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $qty = this.getQty();
        result = result * 59 + ($qty == null ? 43 : $qty.hashCode());
        Object $woCount = this.getWoCount();
        result = result * 59 + ($woCount == null ? 43 : $woCount.hashCode());
        Object $materialCode = this.getMaterialCode();
        result = result * 59 + ($materialCode == null ? 43 : $materialCode.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $unit = this.getUnit();
        result = result * 59 + ($unit == null ? 43 : $unit.hashCode());
        Object $value = this.getValue();
        return result * 59 + ($value == null ? 43 : $value.hashCode());
    }

    
    @Override
    public String toString() {
        return "MaterialConsumptionRow(materialCode="
            + this.getMaterialCode()
            + ", name="
            + this.getName()
            + ", unit="
            + this.getUnit()
            + ", qty="
            + this.getQty()
            + ", value="
            + this.getValue()
            + ", woCount="
            + this.getWoCount()
            + ")";
    }
}
