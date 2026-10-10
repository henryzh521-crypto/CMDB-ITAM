package com.dcim.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("attachment")
public class AttachmentEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String ownerType;
    private String ownerId;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private String storageKey;
    private String uploadedBy;
    private LocalDateTime createdAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getOwnerType() {
        return this.ownerType;
    }

    
    public String getOwnerId() {
        return this.ownerId;
    }

    
    public String getOriginalName() {
        return this.originalName;
    }

    
    public String getContentType() {
        return this.contentType;
    }

    
    public Long getSizeBytes() {
        return this.sizeBytes;
    }

    
    public String getStorageKey() {
        return this.storageKey;
    }

    
    public String getUploadedBy() {
        return this.uploadedBy;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setOwnerType(final String ownerType) {
        this.ownerType = ownerType;
    }

    
    public void setOwnerId(final String ownerId) {
        this.ownerId = ownerId;
    }

    
    public void setOriginalName(final String originalName) {
        this.originalName = originalName;
    }

    
    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    
    public void setSizeBytes(final Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    
    public void setStorageKey(final String storageKey) {
        this.storageKey = storageKey;
    }

    
    public void setUploadedBy(final String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof AttachmentEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$sizeBytes = this.getSizeBytes();
                Object other$sizeBytes = other.getSizeBytes();
                if (this$sizeBytes == null ? other$sizeBytes == null : this$sizeBytes.equals(other$sizeBytes)) {
                    Object this$ownerType = this.getOwnerType();
                    Object other$ownerType = other.getOwnerType();
                    if (this$ownerType == null ? other$ownerType == null : this$ownerType.equals(other$ownerType)) {
                        Object this$ownerId = this.getOwnerId();
                        Object other$ownerId = other.getOwnerId();
                        if (this$ownerId == null ? other$ownerId == null : this$ownerId.equals(other$ownerId)) {
                            Object this$originalName = this.getOriginalName();
                            Object other$originalName = other.getOriginalName();
                            if (this$originalName == null ? other$originalName == null : this$originalName.equals(other$originalName)) {
                                Object this$contentType = this.getContentType();
                                Object other$contentType = other.getContentType();
                                if (this$contentType == null ? other$contentType == null : this$contentType.equals(other$contentType)) {
                                    Object this$storageKey = this.getStorageKey();
                                    Object other$storageKey = other.getStorageKey();
                                    if (this$storageKey == null ? other$storageKey == null : this$storageKey.equals(other$storageKey)) {
                                        Object this$uploadedBy = this.getUploadedBy();
                                        Object other$uploadedBy = other.getUploadedBy();
                                        if (this$uploadedBy == null ? other$uploadedBy == null : this$uploadedBy.equals(other$uploadedBy)) {
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
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof AttachmentEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $sizeBytes = this.getSizeBytes();
        result = result * 59 + ($sizeBytes == null ? 43 : $sizeBytes.hashCode());
        Object $ownerType = this.getOwnerType();
        result = result * 59 + ($ownerType == null ? 43 : $ownerType.hashCode());
        Object $ownerId = this.getOwnerId();
        result = result * 59 + ($ownerId == null ? 43 : $ownerId.hashCode());
        Object $originalName = this.getOriginalName();
        result = result * 59 + ($originalName == null ? 43 : $originalName.hashCode());
        Object $contentType = this.getContentType();
        result = result * 59 + ($contentType == null ? 43 : $contentType.hashCode());
        Object $storageKey = this.getStorageKey();
        result = result * 59 + ($storageKey == null ? 43 : $storageKey.hashCode());
        Object $uploadedBy = this.getUploadedBy();
        result = result * 59 + ($uploadedBy == null ? 43 : $uploadedBy.hashCode());
        Object $createdAt = this.getCreatedAt();
        return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "AttachmentEntity(id="
            + this.getId()
            + ", ownerType="
            + this.getOwnerType()
            + ", ownerId="
            + this.getOwnerId()
            + ", originalName="
            + this.getOriginalName()
            + ", contentType="
            + this.getContentType()
            + ", sizeBytes="
            + this.getSizeBytes()
            + ", storageKey="
            + this.getStorageKey()
            + ", uploadedBy="
            + this.getUploadedBy()
            + ", createdAt="
            + this.getCreatedAt()
            + ")";
    }
}
