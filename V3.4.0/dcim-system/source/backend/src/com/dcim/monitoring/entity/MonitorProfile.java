package com.dcim.monitoring.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("monitor_profile")
public class MonitorProfile {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String job;
    private String categoryPattern;
    private String ipField;
    private Integer port;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getJob() {
        return this.job;
    }

    
    public String getCategoryPattern() {
        return this.categoryPattern;
    }

    
    public String getIpField() {
        return this.ipField;
    }

    
    public Integer getPort() {
        return this.port;
    }

    
    public Boolean getEnabled() {
        return this.enabled;
    }

    
    public String getRemark() {
        return this.remark;
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

    
    public void setJob(final String job) {
        this.job = job;
    }

    
    public void setCategoryPattern(final String categoryPattern) {
        this.categoryPattern = categoryPattern;
    }

    
    public void setIpField(final String ipField) {
        this.ipField = ipField;
    }

    
    public void setPort(final Integer port) {
        this.port = port;
    }

    
    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }

    
    public void setRemark(final String remark) {
        this.remark = remark;
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
        } else if (!(o instanceof MonitorProfile other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$port = this.getPort();
                Object other$port = other.getPort();
                if (this$port == null ? other$port == null : this$port.equals(other$port)) {
                    Object this$enabled = this.getEnabled();
                    Object other$enabled = other.getEnabled();
                    if (this$enabled == null ? other$enabled == null : this$enabled.equals(other$enabled)) {
                        Object this$job = this.getJob();
                        Object other$job = other.getJob();
                        if (this$job == null ? other$job == null : this$job.equals(other$job)) {
                            Object this$categoryPattern = this.getCategoryPattern();
                            Object other$categoryPattern = other.getCategoryPattern();
                            if (this$categoryPattern == null ? other$categoryPattern == null : this$categoryPattern.equals(other$categoryPattern)) {
                                Object this$ipField = this.getIpField();
                                Object other$ipField = other.getIpField();
                                if (this$ipField == null ? other$ipField == null : this$ipField.equals(other$ipField)) {
                                    Object this$remark = this.getRemark();
                                    Object other$remark = other.getRemark();
                                    if (this$remark == null ? other$remark == null : this$remark.equals(other$remark)) {
                                        Object this$createdAt = this.getCreatedAt();
                                        Object other$createdAt = other.getCreatedAt();
                                        if (this$createdAt == null ? other$createdAt == null : this$createdAt.equals(other$createdAt)) {
                                            Object this$updatedAt = this.getUpdatedAt();
                                            Object other$updatedAt = other.getUpdatedAt();
                                            return this$updatedAt == null ? other$updatedAt == null : this$updatedAt.equals(other$updatedAt);
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
        return other instanceof MonitorProfile;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $port = this.getPort();
        result = result * 59 + ($port == null ? 43 : $port.hashCode());
        Object $enabled = this.getEnabled();
        result = result * 59 + ($enabled == null ? 43 : $enabled.hashCode());
        Object $job = this.getJob();
        result = result * 59 + ($job == null ? 43 : $job.hashCode());
        Object $categoryPattern = this.getCategoryPattern();
        result = result * 59 + ($categoryPattern == null ? 43 : $categoryPattern.hashCode());
        Object $ipField = this.getIpField();
        result = result * 59 + ($ipField == null ? 43 : $ipField.hashCode());
        Object $remark = this.getRemark();
        result = result * 59 + ($remark == null ? 43 : $remark.hashCode());
        Object $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
        Object $updatedAt = this.getUpdatedAt();
        return result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "MonitorProfile(id="
            + this.getId()
            + ", job="
            + this.getJob()
            + ", categoryPattern="
            + this.getCategoryPattern()
            + ", ipField="
            + this.getIpField()
            + ", port="
            + this.getPort()
            + ", enabled="
            + this.getEnabled()
            + ", remark="
            + this.getRemark()
            + ", createdAt="
            + this.getCreatedAt()
            + ", updatedAt="
            + this.getUpdatedAt()
            + ")";
    }
}
