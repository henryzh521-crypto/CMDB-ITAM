package com.dcim.monitoring.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.StringArrayTypeHandler;
import java.time.LocalDateTime;
import java.util.List;

@TableName(
    value = "maintenance_window",
    autoResultMap = true
)
public class MaintenanceWindow {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String name;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    @TableField(
        typeHandler = StringArrayTypeHandler.class
    )
    private List<String> assetNos;
    @TableField(
        typeHandler = StringArrayTypeHandler.class
    )
    private List<String> rackCodes;
    @TableField(
        typeHandler = StringArrayTypeHandler.class
    )
    private List<String> roomCodes;
    @TableField(
        typeHandler = StringArrayTypeHandler.class
    )
    private List<String> alertnames;
    private String reason;
    private String createdBy;
    private LocalDateTime createdAt;
    @TableLogic
    private LocalDateTime deletedAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getName() {
        return this.name;
    }

    
    public LocalDateTime getStartsAt() {
        return this.startsAt;
    }

    
    public LocalDateTime getEndsAt() {
        return this.endsAt;
    }

    
    public List<String> getAssetNos() {
        return this.assetNos;
    }

    
    public List<String> getRackCodes() {
        return this.rackCodes;
    }

    
    public List<String> getRoomCodes() {
        return this.roomCodes;
    }

    
    public List<String> getAlertnames() {
        return this.alertnames;
    }

    
    public String getReason() {
        return this.reason;
    }

    
    public String getCreatedBy() {
        return this.createdBy;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public LocalDateTime getDeletedAt() {
        return this.deletedAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setStartsAt(final LocalDateTime startsAt) {
        this.startsAt = startsAt;
    }

    
    public void setEndsAt(final LocalDateTime endsAt) {
        this.endsAt = endsAt;
    }

    
    public void setAssetNos(final List<String> assetNos) {
        this.assetNos = assetNos;
    }

    
    public void setRackCodes(final List<String> rackCodes) {
        this.rackCodes = rackCodes;
    }

    
    public void setRoomCodes(final List<String> roomCodes) {
        this.roomCodes = roomCodes;
    }

    
    public void setAlertnames(final List<String> alertnames) {
        this.alertnames = alertnames;
    }

    
    public void setReason(final String reason) {
        this.reason = reason;
    }

    
    public void setCreatedBy(final String createdBy) {
        this.createdBy = createdBy;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    public void setDeletedAt(final LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof MaintenanceWindow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$name = this.getName();
                Object other$name = other.getName();
                if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                    Object this$startsAt = this.getStartsAt();
                    Object other$startsAt = other.getStartsAt();
                    if (this$startsAt == null ? other$startsAt == null : this$startsAt.equals(other$startsAt)) {
                        Object this$endsAt = this.getEndsAt();
                        Object other$endsAt = other.getEndsAt();
                        if (this$endsAt == null ? other$endsAt == null : this$endsAt.equals(other$endsAt)) {
                            Object this$assetNos = this.getAssetNos();
                            Object other$assetNos = other.getAssetNos();
                            if (this$assetNos == null ? other$assetNos == null : this$assetNos.equals(other$assetNos)) {
                                Object this$rackCodes = this.getRackCodes();
                                Object other$rackCodes = other.getRackCodes();
                                if (this$rackCodes == null ? other$rackCodes == null : this$rackCodes.equals(other$rackCodes)) {
                                    Object this$roomCodes = this.getRoomCodes();
                                    Object other$roomCodes = other.getRoomCodes();
                                    if (this$roomCodes == null ? other$roomCodes == null : this$roomCodes.equals(other$roomCodes)) {
                                        Object this$alertnames = this.getAlertnames();
                                        Object other$alertnames = other.getAlertnames();
                                        if (this$alertnames == null ? other$alertnames == null : this$alertnames.equals(other$alertnames)) {
                                            Object this$reason = this.getReason();
                                            Object other$reason = other.getReason();
                                            if (this$reason == null ? other$reason == null : this$reason.equals(other$reason)) {
                                                Object this$createdBy = this.getCreatedBy();
                                                Object other$createdBy = other.getCreatedBy();
                                                if (this$createdBy == null ? other$createdBy == null : this$createdBy.equals(other$createdBy)) {
                                                    Object this$createdAt = this.getCreatedAt();
                                                    Object other$createdAt = other.getCreatedAt();
                                                    if (this$createdAt == null ? other$createdAt == null : this$createdAt.equals(other$createdAt)) {
                                                        Object this$deletedAt = this.getDeletedAt();
                                                        Object other$deletedAt = other.getDeletedAt();
                                                        return this$deletedAt == null ? other$deletedAt == null : this$deletedAt.equals(other$deletedAt);
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
        return other instanceof MaintenanceWindow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $startsAt = this.getStartsAt();
        result = result * 59 + ($startsAt == null ? 43 : $startsAt.hashCode());
        Object $endsAt = this.getEndsAt();
        result = result * 59 + ($endsAt == null ? 43 : $endsAt.hashCode());
        Object $assetNos = this.getAssetNos();
        result = result * 59 + ($assetNos == null ? 43 : $assetNos.hashCode());
        Object $rackCodes = this.getRackCodes();
        result = result * 59 + ($rackCodes == null ? 43 : $rackCodes.hashCode());
        Object $roomCodes = this.getRoomCodes();
        result = result * 59 + ($roomCodes == null ? 43 : $roomCodes.hashCode());
        Object $alertnames = this.getAlertnames();
        result = result * 59 + ($alertnames == null ? 43 : $alertnames.hashCode());
        Object $reason = this.getReason();
        result = result * 59 + ($reason == null ? 43 : $reason.hashCode());
        Object $createdBy = this.getCreatedBy();
        result = result * 59 + ($createdBy == null ? 43 : $createdBy.hashCode());
        Object $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
        Object $deletedAt = this.getDeletedAt();
        return result * 59 + ($deletedAt == null ? 43 : $deletedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "MaintenanceWindow(id="
            + this.getId()
            + ", name="
            + this.getName()
            + ", startsAt="
            + this.getStartsAt()
            + ", endsAt="
            + this.getEndsAt()
            + ", assetNos="
            + this.getAssetNos()
            + ", rackCodes="
            + this.getRackCodes()
            + ", roomCodes="
            + this.getRoomCodes()
            + ", alertnames="
            + this.getAlertnames()
            + ", reason="
            + this.getReason()
            + ", createdBy="
            + this.getCreatedBy()
            + ", createdAt="
            + this.getCreatedAt()
            + ", deletedAt="
            + this.getDeletedAt()
            + ")";
    }
}
