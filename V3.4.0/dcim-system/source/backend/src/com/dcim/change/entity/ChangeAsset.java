package com.dcim.change.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("change_asset")
public class ChangeAsset {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private Long crId;
    private String assetNo;
    private LocalDateTime createdAt;

    
    public Long getId() {
        return this.id;
    }

    
    public Long getCrId() {
        return this.crId;
    }

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setCrId(final Long crId) {
        this.crId = crId;
    }

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof ChangeAsset other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$crId = this.getCrId();
                Object other$crId = other.getCrId();
                if (this$crId == null ? other$crId == null : this$crId.equals(other$crId)) {
                    Object this$assetNo = this.getAssetNo();
                    Object other$assetNo = other.getAssetNo();
                    if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
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
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof ChangeAsset;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $crId = this.getCrId();
        result = result * 59 + ($crId == null ? 43 : $crId.hashCode());
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $createdAt = this.getCreatedAt();
        return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "ChangeAsset(id=" + this.getId() + ", crId=" + this.getCrId() + ", assetNo=" + this.getAssetNo() + ", createdAt=" + this.getCreatedAt() + ")";
    }
}
