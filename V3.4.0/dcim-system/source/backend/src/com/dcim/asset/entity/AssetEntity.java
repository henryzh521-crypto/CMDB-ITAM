package com.dcim.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.JsonbTypeHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(
    value = "asset",
    autoResultMap = true
)
public class AssetEntity {
    @TableId(
        type = IdType.INPUT
    )
    private String assetNo;
    private String sn;
    private String name;
    private String categoryCode;
    private String modelCode;
    private String ownerType;
    private String statusBiz;
    private String statusFin;
    private String regionCode;
    private String roomCode;
    private String rackCode;
    private Integer unitNo;
    private Integer uHeight;
    private String department;
    private String owner;
    private String mgmtIp;
    private String bizIp;
    private LocalDate purchaseDate;
    private BigDecimal purchasePrice;
    private LocalDate warrantyEnd;
    private LocalDate rackDate;
    private String contractNo;
    private Integer ratedPowerW;
    private String parentAssetNo;
    @TableField(
        typeHandler = JsonbTypeHandler.class
    )
    private Map<String, Object> attrs;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public String getSn() {
        return this.sn;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getCategoryCode() {
        return this.categoryCode;
    }

    
    public String getModelCode() {
        return this.modelCode;
    }

    
    public String getOwnerType() {
        return this.ownerType;
    }

    
    public String getStatusBiz() {
        return this.statusBiz;
    }

    
    public String getStatusFin() {
        return this.statusFin;
    }

    
    public String getRegionCode() {
        return this.regionCode;
    }

    
    public String getRoomCode() {
        return this.roomCode;
    }

    
    public String getRackCode() {
        return this.rackCode;
    }

    
    public Integer getUnitNo() {
        return this.unitNo;
    }

    
    public Integer getUHeight() {
        return this.uHeight;
    }

    
    public String getDepartment() {
        return this.department;
    }

    
    public String getOwner() {
        return this.owner;
    }

    
    public String getMgmtIp() {
        return this.mgmtIp;
    }

    
    public String getBizIp() {
        return this.bizIp;
    }

    
    public LocalDate getPurchaseDate() {
        return this.purchaseDate;
    }

    
    public BigDecimal getPurchasePrice() {
        return this.purchasePrice;
    }

    
    public LocalDate getWarrantyEnd() {
        return this.warrantyEnd;
    }

    
    public LocalDate getRackDate() {
        return this.rackDate;
    }

    
    public String getContractNo() {
        return this.contractNo;
    }

    
    public Integer getRatedPowerW() {
        return this.ratedPowerW;
    }

    
    public String getParentAssetNo() {
        return this.parentAssetNo;
    }

    
    public Map<String, Object> getAttrs() {
        return this.attrs;
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

    
    public LocalDateTime getDeletedAt() {
        return this.deletedAt;
    }

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
    }

    
    public void setSn(final String sn) {
        this.sn = sn;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setCategoryCode(final String categoryCode) {
        this.categoryCode = categoryCode;
    }

    
    public void setModelCode(final String modelCode) {
        this.modelCode = modelCode;
    }

    
    public void setOwnerType(final String ownerType) {
        this.ownerType = ownerType;
    }

    
    public void setStatusBiz(final String statusBiz) {
        this.statusBiz = statusBiz;
    }

    
    public void setStatusFin(final String statusFin) {
        this.statusFin = statusFin;
    }

    
    public void setRegionCode(final String regionCode) {
        this.regionCode = regionCode;
    }

    
    public void setRoomCode(final String roomCode) {
        this.roomCode = roomCode;
    }

    
    public void setRackCode(final String rackCode) {
        this.rackCode = rackCode;
    }

    
    public void setUnitNo(final Integer unitNo) {
        this.unitNo = unitNo;
    }

    
    public void setUHeight(final Integer uHeight) {
        this.uHeight = uHeight;
    }

    
    public void setDepartment(final String department) {
        this.department = department;
    }

    
    public void setOwner(final String owner) {
        this.owner = owner;
    }

    
    public void setMgmtIp(final String mgmtIp) {
        this.mgmtIp = mgmtIp;
    }

    
    public void setBizIp(final String bizIp) {
        this.bizIp = bizIp;
    }

    
    public void setPurchaseDate(final LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    
    public void setPurchasePrice(final BigDecimal purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    
    public void setWarrantyEnd(final LocalDate warrantyEnd) {
        this.warrantyEnd = warrantyEnd;
    }

    
    public void setRackDate(final LocalDate rackDate) {
        this.rackDate = rackDate;
    }

    
    public void setContractNo(final String contractNo) {
        this.contractNo = contractNo;
    }

    
    public void setRatedPowerW(final Integer ratedPowerW) {
        this.ratedPowerW = ratedPowerW;
    }

    
    public void setParentAssetNo(final String parentAssetNo) {
        this.parentAssetNo = parentAssetNo;
    }

    
    public void setAttrs(final Map<String, Object> attrs) {
        this.attrs = attrs;
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

    
    public void setDeletedAt(final LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof AssetEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$unitNo = this.getUnitNo();
            Object other$unitNo = other.getUnitNo();
            if (this$unitNo == null ? other$unitNo == null : this$unitNo.equals(other$unitNo)) {
                Object this$uHeight = this.getUHeight();
                Object other$uHeight = other.getUHeight();
                if (this$uHeight == null ? other$uHeight == null : this$uHeight.equals(other$uHeight)) {
                    Object this$ratedPowerW = this.getRatedPowerW();
                    Object other$ratedPowerW = other.getRatedPowerW();
                    if (this$ratedPowerW == null ? other$ratedPowerW == null : this$ratedPowerW.equals(other$ratedPowerW)) {
                        Object this$version = this.getVersion();
                        Object other$version = other.getVersion();
                        if (this$version == null ? other$version == null : this$version.equals(other$version)) {
                            Object this$assetNo = this.getAssetNo();
                            Object other$assetNo = other.getAssetNo();
                            if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                                Object this$sn = this.getSn();
                                Object other$sn = other.getSn();
                                if (this$sn == null ? other$sn == null : this$sn.equals(other$sn)) {
                                    Object this$name = this.getName();
                                    Object other$name = other.getName();
                                    if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                                        Object this$categoryCode = this.getCategoryCode();
                                        Object other$categoryCode = other.getCategoryCode();
                                        if (this$categoryCode == null ? other$categoryCode == null : this$categoryCode.equals(other$categoryCode)) {
                                            Object this$modelCode = this.getModelCode();
                                            Object other$modelCode = other.getModelCode();
                                            if (this$modelCode == null ? other$modelCode == null : this$modelCode.equals(other$modelCode)) {
                                                Object this$ownerType = this.getOwnerType();
                                                Object other$ownerType = other.getOwnerType();
                                                if (this$ownerType == null ? other$ownerType == null : this$ownerType.equals(other$ownerType)) {
                                                    Object this$statusBiz = this.getStatusBiz();
                                                    Object other$statusBiz = other.getStatusBiz();
                                                    if (this$statusBiz == null ? other$statusBiz == null : this$statusBiz.equals(other$statusBiz)) {
                                                        Object this$statusFin = this.getStatusFin();
                                                        Object other$statusFin = other.getStatusFin();
                                                        if (this$statusFin == null ? other$statusFin == null : this$statusFin.equals(other$statusFin)) {
                                                            Object this$regionCode = this.getRegionCode();
                                                            Object other$regionCode = other.getRegionCode();
                                                            if (this$regionCode == null ? other$regionCode == null : this$regionCode.equals(other$regionCode)) {
                                                                Object this$roomCode = this.getRoomCode();
                                                                Object other$roomCode = other.getRoomCode();
                                                                if (this$roomCode == null ? other$roomCode == null : this$roomCode.equals(other$roomCode)) {
                                                                    Object this$rackCode = this.getRackCode();
                                                                    Object other$rackCode = other.getRackCode();
                                                                    if (this$rackCode == null ? other$rackCode == null : this$rackCode.equals(other$rackCode)) {
                                                                        Object this$department = this.getDepartment();
                                                                        Object other$department = other.getDepartment();
                                                                        if (this$department == null
                                                                            ? other$department == null
                                                                            : this$department.equals(other$department)) {
                                                                            Object this$owner = this.getOwner();
                                                                            Object other$owner = other.getOwner();
                                                                            if (this$owner == null ? other$owner == null : this$owner.equals(other$owner)) {
                                                                                Object this$mgmtIp = this.getMgmtIp();
                                                                                Object other$mgmtIp = other.getMgmtIp();
                                                                                if (this$mgmtIp == null
                                                                                    ? other$mgmtIp == null
                                                                                    : this$mgmtIp.equals(other$mgmtIp)) {
                                                                                    Object this$bizIp = this.getBizIp();
                                                                                    Object other$bizIp = other.getBizIp();
                                                                                    if (this$bizIp == null
                                                                                        ? other$bizIp == null
                                                                                        : this$bizIp.equals(other$bizIp)) {
                                                                                        Object this$purchaseDate = this.getPurchaseDate();
                                                                                        Object other$purchaseDate = other.getPurchaseDate();
                                                                                        if (this$purchaseDate == null
                                                                                            ? other$purchaseDate == null
                                                                                            : this$purchaseDate.equals(other$purchaseDate)) {
                                                                                            Object this$purchasePrice = this.getPurchasePrice();
                                                                                            Object other$purchasePrice = other.getPurchasePrice();
                                                                                            if (this$purchasePrice == null
                                                                                                ? other$purchasePrice == null
                                                                                                : this$purchasePrice.equals(other$purchasePrice)) {
                                                                                                Object this$warrantyEnd = this.getWarrantyEnd();
                                                                                                Object other$warrantyEnd = other.getWarrantyEnd();
                                                                                                if (this$warrantyEnd == null
                                                                                                    ? other$warrantyEnd == null
                                                                                                    : this$warrantyEnd.equals(other$warrantyEnd)) {
                                                                                                    Object this$rackDate = this.getRackDate();
                                                                                                    Object other$rackDate = other.getRackDate();
                                                                                                    if (this$rackDate == null
                                                                                                        ? other$rackDate == null
                                                                                                        : this$rackDate.equals(other$rackDate)) {
                                                                                                        Object this$contractNo = this.getContractNo();
                                                                                                        Object other$contractNo = other.getContractNo();
                                                                                                        if (this$contractNo == null
                                                                                                            ? other$contractNo == null
                                                                                                            : this$contractNo.equals(other$contractNo)) {
                                                                                                            Object this$parentAssetNo = this.getParentAssetNo();
                                                                                                            Object other$parentAssetNo = other.getParentAssetNo();
                                                                                                            if (this$parentAssetNo == null
                                                                                                                ? other$parentAssetNo == null
                                                                                                                : this$parentAssetNo.equals(other$parentAssetNo)
                                                                                                                )
                                                                                                             {
                                                                                                                Object this$attrs = this.getAttrs();
                                                                                                                Object other$attrs = other.getAttrs();
                                                                                                                if (this$attrs == null
                                                                                                                    ? other$attrs == null
                                                                                                                    : this$attrs.equals(other$attrs)) {
                                                                                                                    Object this$createdAt = this.getCreatedAt();
                                                                                                                    Object other$createdAt = other.getCreatedAt();
                                                                                                                    if (this$createdAt == null
                                                                                                                        ? other$createdAt == null
                                                                                                                        : this$createdAt.equals(other$createdAt)
                                                                                                                        )
                                                                                                                     {
                                                                                                                        Object this$updatedAt = this.getUpdatedAt();
                                                                                                                        Object other$updatedAt = other.getUpdatedAt();
                                                                                                                        if (this$updatedAt == null
                                                                                                                            ? other$updatedAt == null
                                                                                                                            : this$updatedAt.equals(
                                                                                                                                other$updatedAt
                                                                                                                            )) {
                                                                                                                            Object this$deletedAt = this.getDeletedAt();
                                                                                                                            Object other$deletedAt = other.getDeletedAt();
                                                                                                                            return this$deletedAt == null
                                                                                                                                ? other$deletedAt == null
                                                                                                                                : this$deletedAt.equals(
                                                                                                                                    other$deletedAt
                                                                                                                                );
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
        return other instanceof AssetEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $unitNo = this.getUnitNo();
        result = result * 59 + ($unitNo == null ? 43 : $unitNo.hashCode());
        Object $uHeight = this.getUHeight();
        result = result * 59 + ($uHeight == null ? 43 : $uHeight.hashCode());
        Object $ratedPowerW = this.getRatedPowerW();
        result = result * 59 + ($ratedPowerW == null ? 43 : $ratedPowerW.hashCode());
        Object $version = this.getVersion();
        result = result * 59 + ($version == null ? 43 : $version.hashCode());
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $sn = this.getSn();
        result = result * 59 + ($sn == null ? 43 : $sn.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $categoryCode = this.getCategoryCode();
        result = result * 59 + ($categoryCode == null ? 43 : $categoryCode.hashCode());
        Object $modelCode = this.getModelCode();
        result = result * 59 + ($modelCode == null ? 43 : $modelCode.hashCode());
        Object $ownerType = this.getOwnerType();
        result = result * 59 + ($ownerType == null ? 43 : $ownerType.hashCode());
        Object $statusBiz = this.getStatusBiz();
        result = result * 59 + ($statusBiz == null ? 43 : $statusBiz.hashCode());
        Object $statusFin = this.getStatusFin();
        result = result * 59 + ($statusFin == null ? 43 : $statusFin.hashCode());
        Object $regionCode = this.getRegionCode();
        result = result * 59 + ($regionCode == null ? 43 : $regionCode.hashCode());
        Object $roomCode = this.getRoomCode();
        result = result * 59 + ($roomCode == null ? 43 : $roomCode.hashCode());
        Object $rackCode = this.getRackCode();
        result = result * 59 + ($rackCode == null ? 43 : $rackCode.hashCode());
        Object $department = this.getDepartment();
        result = result * 59 + ($department == null ? 43 : $department.hashCode());
        Object $owner = this.getOwner();
        result = result * 59 + ($owner == null ? 43 : $owner.hashCode());
        Object $mgmtIp = this.getMgmtIp();
        result = result * 59 + ($mgmtIp == null ? 43 : $mgmtIp.hashCode());
        Object $bizIp = this.getBizIp();
        result = result * 59 + ($bizIp == null ? 43 : $bizIp.hashCode());
        Object $purchaseDate = this.getPurchaseDate();
        result = result * 59 + ($purchaseDate == null ? 43 : $purchaseDate.hashCode());
        Object $purchasePrice = this.getPurchasePrice();
        result = result * 59 + ($purchasePrice == null ? 43 : $purchasePrice.hashCode());
        Object $warrantyEnd = this.getWarrantyEnd();
        result = result * 59 + ($warrantyEnd == null ? 43 : $warrantyEnd.hashCode());
        Object $rackDate = this.getRackDate();
        result = result * 59 + ($rackDate == null ? 43 : $rackDate.hashCode());
        Object $contractNo = this.getContractNo();
        result = result * 59 + ($contractNo == null ? 43 : $contractNo.hashCode());
        Object $parentAssetNo = this.getParentAssetNo();
        result = result * 59 + ($parentAssetNo == null ? 43 : $parentAssetNo.hashCode());
        Object $attrs = this.getAttrs();
        result = result * 59 + ($attrs == null ? 43 : $attrs.hashCode());
        Object $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
        Object $updatedAt = this.getUpdatedAt();
        result = result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
        Object $deletedAt = this.getDeletedAt();
        return result * 59 + ($deletedAt == null ? 43 : $deletedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "AssetEntity(assetNo="
            + this.getAssetNo()
            + ", sn="
            + this.getSn()
            + ", name="
            + this.getName()
            + ", categoryCode="
            + this.getCategoryCode()
            + ", modelCode="
            + this.getModelCode()
            + ", ownerType="
            + this.getOwnerType()
            + ", statusBiz="
            + this.getStatusBiz()
            + ", statusFin="
            + this.getStatusFin()
            + ", regionCode="
            + this.getRegionCode()
            + ", roomCode="
            + this.getRoomCode()
            + ", rackCode="
            + this.getRackCode()
            + ", unitNo="
            + this.getUnitNo()
            + ", uHeight="
            + this.getUHeight()
            + ", department="
            + this.getDepartment()
            + ", owner="
            + this.getOwner()
            + ", mgmtIp="
            + this.getMgmtIp()
            + ", bizIp="
            + this.getBizIp()
            + ", purchaseDate="
            + this.getPurchaseDate()
            + ", purchasePrice="
            + this.getPurchasePrice()
            + ", warrantyEnd="
            + this.getWarrantyEnd()
            + ", rackDate="
            + this.getRackDate()
            + ", contractNo="
            + this.getContractNo()
            + ", ratedPowerW="
            + this.getRatedPowerW()
            + ", parentAssetNo="
            + this.getParentAssetNo()
            + ", attrs="
            + this.getAttrs()
            + ", version="
            + this.getVersion()
            + ", createdAt="
            + this.getCreatedAt()
            + ", updatedAt="
            + this.getUpdatedAt()
            + ", deletedAt="
            + this.getDeletedAt()
            + ")";
    }
}
