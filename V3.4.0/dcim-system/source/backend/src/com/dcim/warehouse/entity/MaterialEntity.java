package com.dcim.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("material")
public class MaterialEntity {
    @TableId(
        type = IdType.INPUT
    )
    private String materialCode;
    private String name;
    private String category;
    private String type;
    private String brand;
    private String model;
    private String pn;
    private String spec;
    private String unit;
    private Integer stock;
    private Integer safetyStock;
    private Integer maxStock;
    private Integer totalIn;
    private Integer totalOut;
    private BigDecimal price;
    private String location;
    private String warehouseCode;
    private String shelfCode;
    private String layerCode;
    private String supplierCode;
    private String remark;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    
    public String getMaterialCode() {
        return this.materialCode;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getCategory() {
        return this.category;
    }

    
    public String getType() {
        return this.type;
    }

    
    public String getBrand() {
        return this.brand;
    }

    
    public String getModel() {
        return this.model;
    }

    
    public String getPn() {
        return this.pn;
    }

    
    public String getSpec() {
        return this.spec;
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

    
    public String getLocation() {
        return this.location;
    }

    
    public String getWarehouseCode() {
        return this.warehouseCode;
    }

    
    public String getShelfCode() {
        return this.shelfCode;
    }

    
    public String getLayerCode() {
        return this.layerCode;
    }

    
    public String getSupplierCode() {
        return this.supplierCode;
    }

    
    public String getRemark() {
        return this.remark;
    }

    
    public Integer getVersion() {
        return this.version;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
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

    
    public void setType(final String type) {
        this.type = type;
    }

    
    public void setBrand(final String brand) {
        this.brand = brand;
    }

    
    public void setModel(final String model) {
        this.model = model;
    }

    
    public void setPn(final String pn) {
        this.pn = pn;
    }

    
    public void setSpec(final String spec) {
        this.spec = spec;
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

    
    public void setLocation(final String location) {
        this.location = location;
    }

    
    public void setWarehouseCode(final String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    
    public void setShelfCode(final String shelfCode) {
        this.shelfCode = shelfCode;
    }

    
    public void setLayerCode(final String layerCode) {
        this.layerCode = layerCode;
    }

    
    public void setSupplierCode(final String supplierCode) {
        this.supplierCode = supplierCode;
    }

    
    public void setRemark(final String remark) {
        this.remark = remark;
    }

    
    public void setVersion(final Integer version) {
        this.version = version;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    public void setUpdatedAt(final LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof MaterialEntity other)) {
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
                                Object this$version = this.getVersion();
                                Object other$version = other.getVersion();
                                if (this$version == null ? other$version == null : this$version.equals(other$version)) {
                                    Object this$materialCode = this.getMaterialCode();
                                    Object other$materialCode = other.getMaterialCode();
                                    if (this$materialCode == null ? other$materialCode == null : this$materialCode.equals(other$materialCode)) {
                                        Object this$name = this.getName();
                                        Object other$name = other.getName();
                                        if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                                            Object this$category = this.getCategory();
                                            Object other$category = other.getCategory();
                                            if (this$category == null ? other$category == null : this$category.equals(other$category)) {
                                                Object this$type = this.getType();
                                                Object other$type = other.getType();
                                                if (this$type == null ? other$type == null : this$type.equals(other$type)) {
                                                    Object this$brand = this.getBrand();
                                                    Object other$brand = other.getBrand();
                                                    if (this$brand == null ? other$brand == null : this$brand.equals(other$brand)) {
                                                        Object this$model = this.getModel();
                                                        Object other$model = other.getModel();
                                                        if (this$model == null ? other$model == null : this$model.equals(other$model)) {
                                                            Object this$pn = this.getPn();
                                                            Object other$pn = other.getPn();
                                                            if (this$pn == null ? other$pn == null : this$pn.equals(other$pn)) {
                                                                Object this$spec = this.getSpec();
                                                                Object other$spec = other.getSpec();
                                                                if (this$spec == null ? other$spec == null : this$spec.equals(other$spec)) {
                                                                    Object this$unit = this.getUnit();
                                                                    Object other$unit = other.getUnit();
                                                                    if (this$unit == null ? other$unit == null : this$unit.equals(other$unit)) {
                                                                        Object this$price = this.getPrice();
                                                                        Object other$price = other.getPrice();
                                                                        if (this$price == null ? other$price == null : this$price.equals(other$price)) {
                                                                            Object this$location = this.getLocation();
                                                                            Object other$location = other.getLocation();
                                                                            if (this$location == null
                                                                                ? other$location == null
                                                                                : this$location.equals(other$location)) {
                                                                                Object this$warehouseCode = this.getWarehouseCode();
                                                                                Object other$warehouseCode = other.getWarehouseCode();
                                                                                if (this$warehouseCode == null
                                                                                    ? other$warehouseCode == null
                                                                                    : this$warehouseCode.equals(other$warehouseCode)) {
                                                                                    Object this$shelfCode = this.getShelfCode();
                                                                                    Object other$shelfCode = other.getShelfCode();
                                                                                    if (this$shelfCode == null
                                                                                        ? other$shelfCode == null
                                                                                        : this$shelfCode.equals(other$shelfCode)) {
                                                                                        Object this$layerCode = this.getLayerCode();
                                                                                        Object other$layerCode = other.getLayerCode();
                                                                                        if (this$layerCode == null
                                                                                            ? other$layerCode == null
                                                                                            : this$layerCode.equals(other$layerCode)) {
                                                                                            Object this$supplierCode = this.getSupplierCode();
                                                                                            Object other$supplierCode = other.getSupplierCode();
                                                                                            if (this$supplierCode == null
                                                                                                ? other$supplierCode == null
                                                                                                : this$supplierCode.equals(other$supplierCode)) {
                                                                                                Object this$remark = this.getRemark();
                                                                                                Object other$remark = other.getRemark();
                                                                                                if (this$remark == null
                                                                                                    ? other$remark == null
                                                                                                    : this$remark.equals(other$remark)) {
                                                                                                    Object this$createdAt = this.getCreatedAt();
                                                                                                    Object other$createdAt = other.getCreatedAt();
                                                                                                    if (this$createdAt == null
                                                                                                        ? other$createdAt == null
                                                                                                        : this$createdAt.equals(other$createdAt)) {
                                                                                                        Object this$updatedAt = this.getUpdatedAt();
                                                                                                        Object other$updatedAt = other.getUpdatedAt();
                                                                                                        return this$updatedAt == null
                                                                                                            ? other$updatedAt == null
                                                                                                            : this$updatedAt.equals(other$updatedAt);
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
        return other instanceof MaterialEntity;
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
        Object $version = this.getVersion();
        result = result * 59 + ($version == null ? 43 : $version.hashCode());
        Object $materialCode = this.getMaterialCode();
        result = result * 59 + ($materialCode == null ? 43 : $materialCode.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $category = this.getCategory();
        result = result * 59 + ($category == null ? 43 : $category.hashCode());
        Object $type = this.getType();
        result = result * 59 + ($type == null ? 43 : $type.hashCode());
        Object $brand = this.getBrand();
        result = result * 59 + ($brand == null ? 43 : $brand.hashCode());
        Object $model = this.getModel();
        result = result * 59 + ($model == null ? 43 : $model.hashCode());
        Object $pn = this.getPn();
        result = result * 59 + ($pn == null ? 43 : $pn.hashCode());
        Object $spec = this.getSpec();
        result = result * 59 + ($spec == null ? 43 : $spec.hashCode());
        Object $unit = this.getUnit();
        result = result * 59 + ($unit == null ? 43 : $unit.hashCode());
        Object $price = this.getPrice();
        result = result * 59 + ($price == null ? 43 : $price.hashCode());
        Object $location = this.getLocation();
        result = result * 59 + ($location == null ? 43 : $location.hashCode());
        Object $warehouseCode = this.getWarehouseCode();
        result = result * 59 + ($warehouseCode == null ? 43 : $warehouseCode.hashCode());
        Object $shelfCode = this.getShelfCode();
        result = result * 59 + ($shelfCode == null ? 43 : $shelfCode.hashCode());
        Object $layerCode = this.getLayerCode();
        result = result * 59 + ($layerCode == null ? 43 : $layerCode.hashCode());
        Object $supplierCode = this.getSupplierCode();
        result = result * 59 + ($supplierCode == null ? 43 : $supplierCode.hashCode());
        Object $remark = this.getRemark();
        result = result * 59 + ($remark == null ? 43 : $remark.hashCode());
        Object $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
        Object $updatedAt = this.getUpdatedAt();
        return result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "MaterialEntity(materialCode="
            + this.getMaterialCode()
            + ", name="
            + this.getName()
            + ", category="
            + this.getCategory()
            + ", type="
            + this.getType()
            + ", brand="
            + this.getBrand()
            + ", model="
            + this.getModel()
            + ", pn="
            + this.getPn()
            + ", spec="
            + this.getSpec()
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
            + ", location="
            + this.getLocation()
            + ", warehouseCode="
            + this.getWarehouseCode()
            + ", shelfCode="
            + this.getShelfCode()
            + ", layerCode="
            + this.getLayerCode()
            + ", supplierCode="
            + this.getSupplierCode()
            + ", remark="
            + this.getRemark()
            + ", version="
            + this.getVersion()
            + ", createdAt="
            + this.getCreatedAt()
            + ", updatedAt="
            + this.getUpdatedAt()
            + ")";
    }
}
