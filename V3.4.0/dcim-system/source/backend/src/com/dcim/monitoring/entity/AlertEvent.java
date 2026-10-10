package com.dcim.monitoring.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.JsonbTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(
    value = "alert_event",
    autoResultMap = true
)
public class AlertEvent {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String fingerprint;
    private String source;
    private String alertname;
    private String severity;
    private String status;
    private String summary;
    private String description;
    @TableField(
        typeHandler = JsonbTypeHandler.class
    )
    private Map<String, Object> labels;
    private String instance;
    private String assetNo;
    private String rackCode;
    private String roomCode;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private LocalDateTime lastReceivedAt;
    private Integer fireCount;
    private Boolean suppressed;
    private String suppressReason;
    private String ackBy;
    private LocalDateTime ackAt;
    private String ackNote;
    private String woNo;
    private String generatorUrl;
    private String closeReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getFingerprint() {
        return this.fingerprint;
    }

    
    public String getSource() {
        return this.source;
    }

    
    public String getAlertname() {
        return this.alertname;
    }

    
    public String getSeverity() {
        return this.severity;
    }

    
    public String getStatus() {
        return this.status;
    }

    
    public String getSummary() {
        return this.summary;
    }

    
    public String getDescription() {
        return this.description;
    }

    
    public Map<String, Object> getLabels() {
        return this.labels;
    }

    
    public String getInstance() {
        return this.instance;
    }

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public String getRackCode() {
        return this.rackCode;
    }

    
    public String getRoomCode() {
        return this.roomCode;
    }

    
    public LocalDateTime getStartsAt() {
        return this.startsAt;
    }

    
    public LocalDateTime getEndsAt() {
        return this.endsAt;
    }

    
    public LocalDateTime getLastReceivedAt() {
        return this.lastReceivedAt;
    }

    
    public Integer getFireCount() {
        return this.fireCount;
    }

    
    public Boolean getSuppressed() {
        return this.suppressed;
    }

    
    public String getSuppressReason() {
        return this.suppressReason;
    }

    
    public String getAckBy() {
        return this.ackBy;
    }

    
    public LocalDateTime getAckAt() {
        return this.ackAt;
    }

    
    public String getAckNote() {
        return this.ackNote;
    }

    
    public String getWoNo() {
        return this.woNo;
    }

    
    public String getGeneratorUrl() {
        return this.generatorUrl;
    }

    
    public String getCloseReason() {
        return this.closeReason;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setFingerprint(final String fingerprint) {
        this.fingerprint = fingerprint;
    }

    
    public void setSource(final String source) {
        this.source = source;
    }

    
    public void setAlertname(final String alertname) {
        this.alertname = alertname;
    }

    
    public void setSeverity(final String severity) {
        this.severity = severity;
    }

    
    public void setStatus(final String status) {
        this.status = status;
    }

    
    public void setSummary(final String summary) {
        this.summary = summary;
    }

    
    public void setDescription(final String description) {
        this.description = description;
    }

    
    public void setLabels(final Map<String, Object> labels) {
        this.labels = labels;
    }

    
    public void setInstance(final String instance) {
        this.instance = instance;
    }

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
    }

    
    public void setRackCode(final String rackCode) {
        this.rackCode = rackCode;
    }

    
    public void setRoomCode(final String roomCode) {
        this.roomCode = roomCode;
    }

    
    public void setStartsAt(final LocalDateTime startsAt) {
        this.startsAt = startsAt;
    }

    
    public void setEndsAt(final LocalDateTime endsAt) {
        this.endsAt = endsAt;
    }

    
    public void setLastReceivedAt(final LocalDateTime lastReceivedAt) {
        this.lastReceivedAt = lastReceivedAt;
    }

    
    public void setFireCount(final Integer fireCount) {
        this.fireCount = fireCount;
    }

    
    public void setSuppressed(final Boolean suppressed) {
        this.suppressed = suppressed;
    }

    
    public void setSuppressReason(final String suppressReason) {
        this.suppressReason = suppressReason;
    }

    
    public void setAckBy(final String ackBy) {
        this.ackBy = ackBy;
    }

    
    public void setAckAt(final LocalDateTime ackAt) {
        this.ackAt = ackAt;
    }

    
    public void setAckNote(final String ackNote) {
        this.ackNote = ackNote;
    }

    
    public void setWoNo(final String woNo) {
        this.woNo = woNo;
    }

    
    public void setGeneratorUrl(final String generatorUrl) {
        this.generatorUrl = generatorUrl;
    }

    
    public void setCloseReason(final String closeReason) {
        this.closeReason = closeReason;
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
        } else if (!(o instanceof AlertEvent other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$fireCount = this.getFireCount();
                Object other$fireCount = other.getFireCount();
                if (this$fireCount == null ? other$fireCount == null : this$fireCount.equals(other$fireCount)) {
                    Object this$suppressed = this.getSuppressed();
                    Object other$suppressed = other.getSuppressed();
                    if (this$suppressed == null ? other$suppressed == null : this$suppressed.equals(other$suppressed)) {
                        Object this$fingerprint = this.getFingerprint();
                        Object other$fingerprint = other.getFingerprint();
                        if (this$fingerprint == null ? other$fingerprint == null : this$fingerprint.equals(other$fingerprint)) {
                            Object this$source = this.getSource();
                            Object other$source = other.getSource();
                            if (this$source == null ? other$source == null : this$source.equals(other$source)) {
                                Object this$alertname = this.getAlertname();
                                Object other$alertname = other.getAlertname();
                                if (this$alertname == null ? other$alertname == null : this$alertname.equals(other$alertname)) {
                                    Object this$severity = this.getSeverity();
                                    Object other$severity = other.getSeverity();
                                    if (this$severity == null ? other$severity == null : this$severity.equals(other$severity)) {
                                        Object this$status = this.getStatus();
                                        Object other$status = other.getStatus();
                                        if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                                            Object this$summary = this.getSummary();
                                            Object other$summary = other.getSummary();
                                            if (this$summary == null ? other$summary == null : this$summary.equals(other$summary)) {
                                                Object this$description = this.getDescription();
                                                Object other$description = other.getDescription();
                                                if (this$description == null ? other$description == null : this$description.equals(other$description)) {
                                                    Object this$labels = this.getLabels();
                                                    Object other$labels = other.getLabels();
                                                    if (this$labels == null ? other$labels == null : this$labels.equals(other$labels)) {
                                                        Object this$instance = this.getInstance();
                                                        Object other$instance = other.getInstance();
                                                        if (this$instance == null ? other$instance == null : this$instance.equals(other$instance)) {
                                                            Object this$assetNo = this.getAssetNo();
                                                            Object other$assetNo = other.getAssetNo();
                                                            if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                                                                Object this$rackCode = this.getRackCode();
                                                                Object other$rackCode = other.getRackCode();
                                                                if (this$rackCode == null ? other$rackCode == null : this$rackCode.equals(other$rackCode)) {
                                                                    Object this$roomCode = this.getRoomCode();
                                                                    Object other$roomCode = other.getRoomCode();
                                                                    if (this$roomCode == null ? other$roomCode == null : this$roomCode.equals(other$roomCode)) {
                                                                        Object this$startsAt = this.getStartsAt();
                                                                        Object other$startsAt = other.getStartsAt();
                                                                        if (this$startsAt == null
                                                                            ? other$startsAt == null
                                                                            : this$startsAt.equals(other$startsAt)) {
                                                                            Object this$endsAt = this.getEndsAt();
                                                                            Object other$endsAt = other.getEndsAt();
                                                                            if (this$endsAt == null ? other$endsAt == null : this$endsAt.equals(other$endsAt)) {
                                                                                Object this$lastReceivedAt = this.getLastReceivedAt();
                                                                                Object other$lastReceivedAt = other.getLastReceivedAt();
                                                                                if (this$lastReceivedAt == null
                                                                                    ? other$lastReceivedAt == null
                                                                                    : this$lastReceivedAt.equals(other$lastReceivedAt)) {
                                                                                    Object this$suppressReason = this.getSuppressReason();
                                                                                    Object other$suppressReason = other.getSuppressReason();
                                                                                    if (this$suppressReason == null
                                                                                        ? other$suppressReason == null
                                                                                        : this$suppressReason.equals(other$suppressReason)) {
                                                                                        Object this$ackBy = this.getAckBy();
                                                                                        Object other$ackBy = other.getAckBy();
                                                                                        if (this$ackBy == null
                                                                                            ? other$ackBy == null
                                                                                            : this$ackBy.equals(other$ackBy)) {
                                                                                            Object this$ackAt = this.getAckAt();
                                                                                            Object other$ackAt = other.getAckAt();
                                                                                            if (this$ackAt == null
                                                                                                ? other$ackAt == null
                                                                                                : this$ackAt.equals(other$ackAt)) {
                                                                                                Object this$ackNote = this.getAckNote();
                                                                                                Object other$ackNote = other.getAckNote();
                                                                                                if (this$ackNote == null
                                                                                                    ? other$ackNote == null
                                                                                                    : this$ackNote.equals(other$ackNote)) {
                                                                                                    Object this$woNo = this.getWoNo();
                                                                                                    Object other$woNo = other.getWoNo();
                                                                                                    if (this$woNo == null
                                                                                                        ? other$woNo == null
                                                                                                        : this$woNo.equals(other$woNo)) {
                                                                                                        Object this$generatorUrl = this.getGeneratorUrl();
                                                                                                        Object other$generatorUrl = other.getGeneratorUrl();
                                                                                                        if (this$generatorUrl == null
                                                                                                            ? other$generatorUrl == null
                                                                                                            : this$generatorUrl.equals(other$generatorUrl)) {
                                                                                                            Object this$closeReason = this.getCloseReason();
                                                                                                            Object other$closeReason = other.getCloseReason();
                                                                                                            if (this$closeReason == null
                                                                                                                ? other$closeReason == null
                                                                                                                : this$closeReason.equals(other$closeReason)) {
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
        return other instanceof AlertEvent;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $fireCount = this.getFireCount();
        result = result * 59 + ($fireCount == null ? 43 : $fireCount.hashCode());
        Object $suppressed = this.getSuppressed();
        result = result * 59 + ($suppressed == null ? 43 : $suppressed.hashCode());
        Object $fingerprint = this.getFingerprint();
        result = result * 59 + ($fingerprint == null ? 43 : $fingerprint.hashCode());
        Object $source = this.getSource();
        result = result * 59 + ($source == null ? 43 : $source.hashCode());
        Object $alertname = this.getAlertname();
        result = result * 59 + ($alertname == null ? 43 : $alertname.hashCode());
        Object $severity = this.getSeverity();
        result = result * 59 + ($severity == null ? 43 : $severity.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $summary = this.getSummary();
        result = result * 59 + ($summary == null ? 43 : $summary.hashCode());
        Object $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        Object $labels = this.getLabels();
        result = result * 59 + ($labels == null ? 43 : $labels.hashCode());
        Object $instance = this.getInstance();
        result = result * 59 + ($instance == null ? 43 : $instance.hashCode());
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $rackCode = this.getRackCode();
        result = result * 59 + ($rackCode == null ? 43 : $rackCode.hashCode());
        Object $roomCode = this.getRoomCode();
        result = result * 59 + ($roomCode == null ? 43 : $roomCode.hashCode());
        Object $startsAt = this.getStartsAt();
        result = result * 59 + ($startsAt == null ? 43 : $startsAt.hashCode());
        Object $endsAt = this.getEndsAt();
        result = result * 59 + ($endsAt == null ? 43 : $endsAt.hashCode());
        Object $lastReceivedAt = this.getLastReceivedAt();
        result = result * 59 + ($lastReceivedAt == null ? 43 : $lastReceivedAt.hashCode());
        Object $suppressReason = this.getSuppressReason();
        result = result * 59 + ($suppressReason == null ? 43 : $suppressReason.hashCode());
        Object $ackBy = this.getAckBy();
        result = result * 59 + ($ackBy == null ? 43 : $ackBy.hashCode());
        Object $ackAt = this.getAckAt();
        result = result * 59 + ($ackAt == null ? 43 : $ackAt.hashCode());
        Object $ackNote = this.getAckNote();
        result = result * 59 + ($ackNote == null ? 43 : $ackNote.hashCode());
        Object $woNo = this.getWoNo();
        result = result * 59 + ($woNo == null ? 43 : $woNo.hashCode());
        Object $generatorUrl = this.getGeneratorUrl();
        result = result * 59 + ($generatorUrl == null ? 43 : $generatorUrl.hashCode());
        Object $closeReason = this.getCloseReason();
        result = result * 59 + ($closeReason == null ? 43 : $closeReason.hashCode());
        Object $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
        Object $updatedAt = this.getUpdatedAt();
        return result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "AlertEvent(id="
            + this.getId()
            + ", fingerprint="
            + this.getFingerprint()
            + ", source="
            + this.getSource()
            + ", alertname="
            + this.getAlertname()
            + ", severity="
            + this.getSeverity()
            + ", status="
            + this.getStatus()
            + ", summary="
            + this.getSummary()
            + ", description="
            + this.getDescription()
            + ", labels="
            + this.getLabels()
            + ", instance="
            + this.getInstance()
            + ", assetNo="
            + this.getAssetNo()
            + ", rackCode="
            + this.getRackCode()
            + ", roomCode="
            + this.getRoomCode()
            + ", startsAt="
            + this.getStartsAt()
            + ", endsAt="
            + this.getEndsAt()
            + ", lastReceivedAt="
            + this.getLastReceivedAt()
            + ", fireCount="
            + this.getFireCount()
            + ", suppressed="
            + this.getSuppressed()
            + ", suppressReason="
            + this.getSuppressReason()
            + ", ackBy="
            + this.getAckBy()
            + ", ackAt="
            + this.getAckAt()
            + ", ackNote="
            + this.getAckNote()
            + ", woNo="
            + this.getWoNo()
            + ", generatorUrl="
            + this.getGeneratorUrl()
            + ", closeReason="
            + this.getCloseReason()
            + ", createdAt="
            + this.getCreatedAt()
            + ", updatedAt="
            + this.getUpdatedAt()
            + ")";
    }
}
