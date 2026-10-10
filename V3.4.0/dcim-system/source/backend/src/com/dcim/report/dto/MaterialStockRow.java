package com.dcim.report.dto;

import java.math.BigDecimal;

public class MaterialStockRow {
    private String materialCode;
    private String name;
    private String category;
    private String unit;
    private Integer stock;
    private Integer safetyStock;
    private Integer maxStock;
    private Integer totalIn;
    private Integer totalOut;
    private BigDecimal price;
    private String warehouseCode;
    private BigDecimal stockValue;
    private String alert;

    
    public String getMaterialCode() {
        return this.materialCode;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getCategory() {
        return this.category;
    }

    
    public String getUnit() {
        return this.unit;
    }

    
    public Integer getStock() {
        return this.stock;
    }

    
    public Integer getSafetyStock() {
        return this.safetyStock;
    }

    
    public Integer getMaxStock() {
        return this.maxStock;
    }

    
    public Integer getTotalIn() {
        return this.totalIn;
    }

    
    public Integer getTotalOut() {
        return this.totalOut;
    }

    
    public BigDecimal getPrice() {
        return this.price;
    }

    
    public String getWarehouseCode() {
        return this.warehouseCode;
    }

    
    public BigDecimal getStockValue() {
        return this.stockValue;
    }

    
    public String getAlert() {
        return this.alert;
    }

    
    public void setMaterialCode(final String materialCode) {
        this.materialCode = materialCode;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setCategory(final String category) {
        this.category = category;
    }

    
    public void setUnit(final String unit) {
        this.unit = unit;
    }

    
    public void setStock(final Integer stock) {
        this.stock = stock;
    }

    
    public void setSafetyStock(final Integer safetyStock) {
        this.safetyStock = safetyStock;
    }

    
    public void setMaxStock(final Integer maxStock) {
        this.maxStock = maxStock;
    }

    
    public void setTotalIn(final Integer totalIn) {
        this.totalIn = totalIn;
    }

    
    public void setTotalOut(final Integer totalOut) {
        this.totalOut = totalOut;
    }

    
    public void setPrice(final BigDecimal price) {
        this.price = price;
    }

    
    public void setWarehouseCode(final String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    
    public void setStockValue(final BigDecimal stockValue) {
        this.stockValue = stockValue;
    }

    
    public void setAlert(final String alert) {
        this.alert = alert;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof MaterialStockRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$stock = this.getStock();
            Object other$stock = other.getStock();
            if (this$stock == null ? other$stock == null : this$stock.equals(other$stock)) {
                Object this$safetyStock = this.getSafetyStock();
                Object other$safetyStock = other.getSafetyStock();
                if (this$safetyStock == null ? other$safetyStock == null : this$safetyStock.equals(other$safetyStock)) {
                    Object this$maxStock = this.getMaxStock();
                    Object other$maxStock = other.getMaxStock();
                    if (this$maxStock == null ? other$maxStock == null : this$maxStock.equals(other$maxStock)) {
                        Object this$totalIn = this.getTotalIn();
                        Object other$totalIn = other.getTotalIn();
                        if (this$totalIn == null ? other$totalIn == null : this$totalIn.equals(other$totalIn)) {
                            Object this$totalOut = this.getTotalOut();
                            Object other$totalOut = other.getTotalOut();
                            if (this$totalOut == null ? other$totalOut == null : this$totalOut.equals(other$totalOut)) {
                                Object this$materialCode = this.getMaterialCode();
                                Object other$materialCode = other.getMaterialCode();
                                if (this$materialCode == null ? other$materialCode == null : this$materialCode.equals(other$materialCode)) {
                                    Object this$name = this.getName();
                                    Object other$name = other.getName();
                                    if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                                        Object this$category = this.getCategory();
                                        Object other$category = other.getCategory();
                                        if (this$category == null ? other$category == null : this$category.equals(other$category)) {
                                            Object this$unit = this.getUnit();
                                            Object other$unit = other.getUnit();
                                            if (this$unit == null ? other$unit == null : this$unit.equals(other$unit)) {
                                                Object this$price = this.getPrice();
                                                Object other$price = other.getPrice();
                                                if (this$price == null ? other$price == null : this$price.equals(other$price)) {
                                                    Object this$warehouseCode = this.getWarehouseCode();
                                                    Object other$warehouseCode = other.getWarehouseCode();
                                                    if (this$warehouseCode == null
                                                        ? other$warehouseCode == null
                                                        : this$warehouseCode.equals(other$warehouseCode)) {
                                                        Object this$stockValue = this.getStockValue();
                                                        Object other$stockValue = other.getStockValue();
                                                        if (this$stockValue == null ? other$stockValue == null : this$stockValue.equals(other$stockValue)) {
                                                            Object this$alert = this.getAlert();
                                                            Object other$alert = other.getAlert();
                                                            return this$alert == null ? other$alert == null : this$alert.equals(other$alert);
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
        return other instanceof MaterialStockRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $stock = this.getStock();
        result = result * 59 + ($stock == null ? 43 : $stock.hashCode());
        Object $safetyStock = this.getSafetyStock();
        result = result * 59 + ($safetyStock == null ? 43 : $safetyStock.hashCode());
        Object $maxStock = this.getMaxStock();
        result = result * 59 + ($maxStock == null ? 43 : $maxStock.hashCode());
        Object $totalIn = this.getTotalIn();
        result = result * 59 + ($totalIn == null ? 43 : $totalIn.hashCode());
        Object $totalOut = this.getTotalOut();
        result = result * 59 + ($totalOut == null ? 43 : $totalOut.hashCode());
        Object $materialCode = this.getMaterialCode();
        result = result * 59 + ($materialCode == null ? 43 : $materialCode.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $category = this.getCategory();
        result = result * 59 + ($category == null ? 43 : $category.hashCode());
        Object $unit = this.getUnit();
        result = result * 59 + ($unit == null ? 43 : $unit.hashCode());
        Object $price = this.getPrice();
        result = result * 59 + ($price == null ? 43 : $price.hashCode());
        Object $warehouseCode = this.getWarehouseCode();
        result = result * 59 + ($warehouseCode == null ? 43 : $warehouseCode.hashCode());
        Object $stockValue = this.getStockValue();
        result = result * 59 + ($stockValue == null ? 43 : $stockValue.hashCode());
        Object $alert = this.getAlert();
        return result * 59 + ($alert == null ? 43 : $alert.hashCode());
    }

    
    @Override
    public String toString() {
        return "MaterialStockRow(materialCode="
            + this.getMaterialCode()
            + ", name="
            + this.getName()
            + ", category="
            + this.getCategory()
            + ", unit="
            + this.getUnit()
            + ", stock="
            + this.getStock()
            + ", safetyStock="
            + this.getSafetyStock()
            + ", maxStock="
            + this.getMaxStock()
            + ", totalIn="
            + this.getTotalIn()
            + ", totalOut="
            + this.getTotalOut()
            + ", price="
            + this.getPrice()
            + ", warehouseCode="
            + this.getWarehouseCode()
            + ", stockValue="
            + this.getStockValue()
            + ", alert="
            + this.getAlert()
            + ")";
    }
}
