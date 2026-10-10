package com.dcim.webhook.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.JsonbTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(
    value = "webhook_delivery",
    autoResultMap = true
)
public class WebhookDelivery {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private Long endpointId;
    private String eventId;
    private String eventType;
    @TableField(
        typeHandler = JsonbTypeHandler.class
    )
    private Map<String, Object> payload;
    private String status;
    private Integer attempts;
    private String lastError;
    private Integer responseCode;
    private String responseSnippet;
    private Integer durationMs;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    
    public Long getId() {
        return this.id;
    }

    
    public Long getEndpointId() {
        return this.endpointId;
    }

    
    public String getEventId() {
        return this.eventId;
    }

    
    public String getEventType() {
        return this.eventType;
    }

    
    public Map<String, Object> getPayload() {
        return this.payload;
    }

    
    public String getStatus() {
        return this.status;
    }

    
    public Integer getAttempts() {
        return this.attempts;
    }

    
    public String getLastError() {
        return this.lastError;
    }

    
    public Integer getResponseCode() {
        return this.responseCode;
    }

    
    public String getResponseSnippet() {
        return this.responseSnippet;
    }

    
    public Integer getDurationMs() {
        return this.durationMs;
    }

    
    public LocalDateTime getNextAttemptAt() {
        return this.nextAttemptAt;
    }

    
    public LocalDateTime getDeliveredAt() {
        return this.deliveredAt;
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

    
    public void setEndpointId(final Long endpointId) {
        this.endpointId = endpointId;
    }

    
    public void setEventId(final String eventId) {
        this.eventId = eventId;
    }

    
    public void setEventType(final String eventType) {
        this.eventType = eventType;
    }

    
    public void setPayload(final Map<String, Object> payload) {
        this.payload = payload;
    }

    
    public void setStatus(final String status) {
        this.status = status;
    }

    
    public void setAttempts(final Integer attempts) {
        this.attempts = attempts;
    }

    
    public void setLastError(final String lastError) {
        this.lastError = lastError;
    }

    
    public void setResponseCode(final Integer responseCode) {
        this.responseCode = responseCode;
    }

    
    public void setResponseSnippet(final String responseSnippet) {
        this.responseSnippet = responseSnippet;
    }

    
    public void setDurationMs(final Integer durationMs) {
        this.durationMs = durationMs;
    }

    
    public void setNextAttemptAt(final LocalDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    
    public void setDeliveredAt(final LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
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
        } else if (!(o instanceof WebhookDelivery other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$endpointId = this.getEndpointId();
                Object other$endpointId = other.getEndpointId();
                if (this$endpointId == null ? other$endpointId == null : this$endpointId.equals(other$endpointId)) {
                    Object this$attempts = this.getAttempts();
                    Object other$attempts = other.getAttempts();
                    if (this$attempts == null ? other$attempts == null : this$attempts.equals(other$attempts)) {
                        Object this$responseCode = this.getResponseCode();
                        Object other$responseCode = other.getResponseCode();
                        if (this$responseCode == null ? other$responseCode == null : this$responseCode.equals(other$responseCode)) {
                            Object this$durationMs = this.getDurationMs();
                            Object other$durationMs = other.getDurationMs();
                            if (this$durationMs == null ? other$durationMs == null : this$durationMs.equals(other$durationMs)) {
                                Object this$eventId = this.getEventId();
                                Object other$eventId = other.getEventId();
                                if (this$eventId == null ? other$eventId == null : this$eventId.equals(other$eventId)) {
                                    Object this$eventType = this.getEventType();
                                    Object other$eventType = other.getEventType();
                                    if (this$eventType == null ? other$eventType == null : this$eventType.equals(other$eventType)) {
                                        Object this$payload = this.getPayload();
                                        Object other$payload = other.getPayload();
                                        if (this$payload == null ? other$payload == null : this$payload.equals(other$payload)) {
                                            Object this$status = this.getStatus();
                                            Object other$status = other.getStatus();
                                            if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                                                Object this$lastError = this.getLastError();
                                                Object other$lastError = other.getLastError();
                                                if (this$lastError == null ? other$lastError == null : this$lastError.equals(other$lastError)) {
                                                    Object this$responseSnippet = this.getResponseSnippet();
                                                    Object other$responseSnippet = other.getResponseSnippet();
                                                    if (this$responseSnippet == null
                                                        ? other$responseSnippet == null
                                                        : this$responseSnippet.equals(other$responseSnippet)) {
                                                        Object this$nextAttemptAt = this.getNextAttemptAt();
                                                        Object other$nextAttemptAt = other.getNextAttemptAt();
                                                        if (this$nextAttemptAt == null
                                                            ? other$nextAttemptAt == null
                                                            : this$nextAttemptAt.equals(other$nextAttemptAt)) {
                                                            Object this$deliveredAt = this.getDeliveredAt();
                                                            Object other$deliveredAt = other.getDeliveredAt();
                                                            if (this$deliveredAt == null
                                                                ? other$deliveredAt == null
                                                                : this$deliveredAt.equals(other$deliveredAt)) {
                                                                Object this$createdAt = this.getCreatedAt();
                                                                Object other$createdAt = other.getCreatedAt();
                                                                if (this$createdAt == null ? other$createdAt == null : this$createdAt.equals(other$createdAt)) {
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
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof WebhookDelivery;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $endpointId = this.getEndpointId();
        result = result * 59 + ($endpointId == null ? 43 : $endpointId.hashCode());
        Object $attempts = this.getAttempts();
        result = result * 59 + ($attempts == null ? 43 : $attempts.hashCode());
        Object $responseCode = this.getResponseCode();
        result = result * 59 + ($responseCode == null ? 43 : $responseCode.hashCode());
        Object $durationMs = this.getDurationMs();
        result = result * 59 + ($durationMs == null ? 43 : $durationMs.hashCode());
        Object $eventId = this.getEventId();
        result = result * 59 + ($eventId == null ? 43 : $eventId.hashCode());
        Object $eventType = this.getEventType();
        result = result * 59 + ($eventType == null ? 43 : $eventType.hashCode());
        Object $payload = this.getPayload();
        result = result * 59 + ($payload == null ? 43 : $payload.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $lastError = this.getLastError();
        result = result * 59 + ($lastError == null ? 43 : $lastError.hashCode());
        Object $responseSnippet = this.getResponseSnippet();
        result = result * 59 + ($responseSnippet == null ? 43 : $responseSnippet.hashCode());
        Object $nextAttemptAt = this.getNextAttemptAt();
        result = result * 59 + ($nextAttemptAt == null ? 43 : $nextAttemptAt.hashCode());
        Object $deliveredAt = this.getDeliveredAt();
        result = result * 59 + ($deliveredAt == null ? 43 : $deliveredAt.hashCode());
        Object $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
        Object $updatedAt = this.getUpdatedAt();
        return result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "WebhookDelivery(id="
            + this.getId()
            + ", endpointId="
            + this.getEndpointId()
            + ", eventId="
            + this.getEventId()
            + ", eventType="
            + this.getEventType()
            + ", payload="
            + this.getPayload()
            + ", status="
            + this.getStatus()
            + ", attempts="
            + this.getAttempts()
            + ", lastError="
            + this.getLastError()
            + ", responseCode="
            + this.getResponseCode()
            + ", responseSnippet="
            + this.getResponseSnippet()
            + ", durationMs="
            + this.getDurationMs()
            + ", nextAttemptAt="
            + this.getNextAttemptAt()
            + ", deliveredAt="
            + this.getDeliveredAt()
            + ", createdAt="
            + this.getCreatedAt()
            + ", updatedAt="
            + this.getUpdatedAt()
            + ")";
    }
}
