package com.dcim.webhook.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.StringArrayTypeHandler;
import java.time.LocalDateTime;
import java.util.List;

@TableName(
    value = "webhook_endpoint",
    autoResultMap = true
)
public class WebhookEndpoint {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String name;
    private String url;
    private String secret;
    @TableField(
        typeHandler = StringArrayTypeHandler.class
    )
    private List<String> eventTypes;
    private String format;
    private Boolean enabled;
    private String lastStatus;
    private LocalDateTime lastDeliveryAt;
    private Integer consecutiveFailures;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getUrl() {
        return this.url;
    }

    
    public String getSecret() {
        return this.secret;
    }

    
    public List<String> getEventTypes() {
        return this.eventTypes;
    }

    
    public String getFormat() {
        return this.format;
    }

    
    public Boolean getEnabled() {
        return this.enabled;
    }

    
    public String getLastStatus() {
        return this.lastStatus;
    }

    
    public LocalDateTime getLastDeliveryAt() {
        return this.lastDeliveryAt;
    }

    
    public Integer getConsecutiveFailures() {
        return this.consecutiveFailures;
    }

    
    public String getCreatedBy() {
        return this.createdBy;
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

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setUrl(final String url) {
        this.url = url;
    }

    
    public void setSecret(final String secret) {
        this.secret = secret;
    }

    
    public void setEventTypes(final List<String> eventTypes) {
        this.eventTypes = eventTypes;
    }

    
    public void setFormat(final String format) {
        this.format = format;
    }

    
    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }

    
    public void setLastStatus(final String lastStatus) {
        this.lastStatus = lastStatus;
    }

    
    public void setLastDeliveryAt(final LocalDateTime lastDeliveryAt) {
        this.lastDeliveryAt = lastDeliveryAt;
    }

    
    public void setConsecutiveFailures(final Integer consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    
    public void setCreatedBy(final String createdBy) {
        this.createdBy = createdBy;
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
        } else if (!(o instanceof WebhookEndpoint other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$enabled = this.getEnabled();
                Object other$enabled = other.getEnabled();
                if (this$enabled == null ? other$enabled == null : this$enabled.equals(other$enabled)) {
                    Object this$consecutiveFailures = this.getConsecutiveFailures();
                    Object other$consecutiveFailures = other.getConsecutiveFailures();
                    if (this$consecutiveFailures == null ? other$consecutiveFailures == null : this$consecutiveFailures.equals(other$consecutiveFailures)) {
                        Object this$name = this.getName();
                        Object other$name = other.getName();
                        if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                            Object this$url = this.getUrl();
                            Object other$url = other.getUrl();
                            if (this$url == null ? other$url == null : this$url.equals(other$url)) {
                                Object this$secret = this.getSecret();
                                Object other$secret = other.getSecret();
                                if (this$secret == null ? other$secret == null : this$secret.equals(other$secret)) {
                                    Object this$eventTypes = this.getEventTypes();
                                    Object other$eventTypes = other.getEventTypes();
                                    if (this$eventTypes == null ? other$eventTypes == null : this$eventTypes.equals(other$eventTypes)) {
                                        Object this$format = this.getFormat();
                                        Object other$format = other.getFormat();
                                        if (this$format == null ? other$format == null : this$format.equals(other$format)) {
                                            Object this$lastStatus = this.getLastStatus();
                                            Object other$lastStatus = other.getLastStatus();
                                            if (this$lastStatus == null ? other$lastStatus == null : this$lastStatus.equals(other$lastStatus)) {
                                                Object this$lastDeliveryAt = this.getLastDeliveryAt();
                                                Object other$lastDeliveryAt = other.getLastDeliveryAt();
                                                if (this$lastDeliveryAt == null
                                                    ? other$lastDeliveryAt == null
                                                    : this$lastDeliveryAt.equals(other$lastDeliveryAt)) {
                                                    Object this$createdBy = this.getCreatedBy();
                                                    Object other$createdBy = other.getCreatedBy();
                                                    if (this$createdBy == null ? other$createdBy == null : this$createdBy.equals(other$createdBy)) {
                                                        Object this$createdAt = this.getCreatedAt();
                                                        Object other$createdAt = other.getCreatedAt();
                                                        if (this$createdAt == null ? other$createdAt == null : this$createdAt.equals(other$createdAt)) {
                                                            Object this$updatedAt = this.getUpdatedAt();
                                                            Object other$updatedAt = other.getUpdatedAt();
                                                            if (this$updatedAt == null ? other$updatedAt == null : this$updatedAt.equals(other$updatedAt)) {
                                                                Object this$deletedAt = this.getDeletedAt();
                                                                Object other$deletedAt = other.getDeletedAt();
                                                                return this$deletedAt == null
                                                                    ? other$deletedAt == null
                                                                    : this$deletedAt.equals(other$deletedAt);
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
        return other instanceof WebhookEndpoint;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $enabled = this.getEnabled();
        result = result * 59 + ($enabled == null ? 43 : $enabled.hashCode());
        Object $consecutiveFailures = this.getConsecutiveFailures();
        result = result * 59 + ($consecutiveFailures == null ? 43 : $consecutiveFailures.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $url = this.getUrl();
        result = result * 59 + ($url == null ? 43 : $url.hashCode());
        Object $secret = this.getSecret();
        result = result * 59 + ($secret == null ? 43 : $secret.hashCode());
        Object $eventTypes = this.getEventTypes();
        result = result * 59 + ($eventTypes == null ? 43 : $eventTypes.hashCode());
        Object $format = this.getFormat();
        result = result * 59 + ($format == null ? 43 : $format.hashCode());
        Object $lastStatus = this.getLastStatus();
        result = result * 59 + ($lastStatus == null ? 43 : $lastStatus.hashCode());
        Object $lastDeliveryAt = this.getLastDeliveryAt();
        result = result * 59 + ($lastDeliveryAt == null ? 43 : $lastDeliveryAt.hashCode());
        Object $createdBy = this.getCreatedBy();
        result = result * 59 + ($createdBy == null ? 43 : $createdBy.hashCode());
        Object $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
        Object $updatedAt = this.getUpdatedAt();
        result = result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
        Object $deletedAt = this.getDeletedAt();
        return result * 59 + ($deletedAt == null ? 43 : $deletedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "WebhookEndpoint(id="
            + this.getId()
            + ", name="
            + this.getName()
            + ", url="
            + this.getUrl()
            + ", secret="
            + this.getSecret()
            + ", eventTypes="
            + this.getEventTypes()
            + ", format="
            + this.getFormat()
            + ", enabled="
            + this.getEnabled()
            + ", lastStatus="
            + this.getLastStatus()
            + ", lastDeliveryAt="
            + this.getLastDeliveryAt()
            + ", consecutiveFailures="
            + this.getConsecutiveFailures()
            + ", createdBy="
            + this.getCreatedBy()
            + ", createdAt="
            + this.getCreatedAt()
            + ", updatedAt="
            + this.getUpdatedAt()
            + ", deletedAt="
            + this.getDeletedAt()
            + ")";
    }
}
