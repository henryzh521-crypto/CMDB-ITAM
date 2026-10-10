package com.dcim.collector.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("discover_task")
public class DiscoverTaskEntity {
    public static final String ST_PENDING = "待执行";
    public static final String ST_SCANNING = "扫描中";
    public static final String ST_DONE = "已完成";
    public static final String ST_FAILED = "失败";
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String taskNo;
    private String name;
    private String cidr;
    private String protocol;
    private Long credentialId;
    private String regionCode;
    private String status;
    private Integer totalIps;
    private Integer scannedIps;
    private Integer foundCount;
    private Integer matchedCount;
    private Integer pendingCount;
    private LocalDateTime lastRunAt;
    private String createdBy;
    private LocalDateTime createdAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getTaskNo() {
        return this.taskNo;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getCidr() {
        return this.cidr;
    }

    
    public String getProtocol() {
        return this.protocol;
    }

    
    public Long getCredentialId() {
        return this.credentialId;
    }

    
    public String getRegionCode() {
        return this.regionCode;
    }

    
    public String getStatus() {
        return this.status;
    }

    
    public Integer getTotalIps() {
        return this.totalIps;
    }

    
    public Integer getScannedIps() {
        return this.scannedIps;
    }

    
    public Integer getFoundCount() {
        return this.foundCount;
    }

    
    public Integer getMatchedCount() {
        return this.matchedCount;
    }

    
    public Integer getPendingCount() {
        return this.pendingCount;
    }

    
    public LocalDateTime getLastRunAt() {
        return this.lastRunAt;
    }

    
    public String getCreatedBy() {
        return this.createdBy;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setTaskNo(final String taskNo) {
        this.taskNo = taskNo;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setCidr(final String cidr) {
        this.cidr = cidr;
    }

    
    public void setProtocol(final String protocol) {
        this.protocol = protocol;
    }

    
    public void setCredentialId(final Long credentialId) {
        this.credentialId = credentialId;
    }

    
    public void setRegionCode(final String regionCode) {
        this.regionCode = regionCode;
    }

    
    public void setStatus(final String status) {
        this.status = status;
    }

    
    public void setTotalIps(final Integer totalIps) {
        this.totalIps = totalIps;
    }

    
    public void setScannedIps(final Integer scannedIps) {
        this.scannedIps = scannedIps;
    }

    
    public void setFoundCount(final Integer foundCount) {
        this.foundCount = foundCount;
    }

    
    public void setMatchedCount(final Integer matchedCount) {
        this.matchedCount = matchedCount;
    }

    
    public void setPendingCount(final Integer pendingCount) {
        this.pendingCount = pendingCount;
    }

    
    public void setLastRunAt(final LocalDateTime lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    
    public void setCreatedBy(final String createdBy) {
        this.createdBy = createdBy;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof DiscoverTaskEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$credentialId = this.getCredentialId();
                Object other$credentialId = other.getCredentialId();
                if (this$credentialId == null ? other$credentialId == null : this$credentialId.equals(other$credentialId)) {
                    Object this$totalIps = this.getTotalIps();
                    Object other$totalIps = other.getTotalIps();
                    if (this$totalIps == null ? other$totalIps == null : this$totalIps.equals(other$totalIps)) {
                        Object this$scannedIps = this.getScannedIps();
                        Object other$scannedIps = other.getScannedIps();
                        if (this$scannedIps == null ? other$scannedIps == null : this$scannedIps.equals(other$scannedIps)) {
                            Object this$foundCount = this.getFoundCount();
                            Object other$foundCount = other.getFoundCount();
                            if (this$foundCount == null ? other$foundCount == null : this$foundCount.equals(other$foundCount)) {
                                Object this$matchedCount = this.getMatchedCount();
                                Object other$matchedCount = other.getMatchedCount();
                                if (this$matchedCount == null ? other$matchedCount == null : this$matchedCount.equals(other$matchedCount)) {
                                    Object this$pendingCount = this.getPendingCount();
                                    Object other$pendingCount = other.getPendingCount();
                                    if (this$pendingCount == null ? other$pendingCount == null : this$pendingCount.equals(other$pendingCount)) {
                                        Object this$taskNo = this.getTaskNo();
                                        Object other$taskNo = other.getTaskNo();
                                        if (this$taskNo == null ? other$taskNo == null : this$taskNo.equals(other$taskNo)) {
                                            Object this$name = this.getName();
                                            Object other$name = other.getName();
                                            if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                                                Object this$cidr = this.getCidr();
                                                Object other$cidr = other.getCidr();
                                                if (this$cidr == null ? other$cidr == null : this$cidr.equals(other$cidr)) {
                                                    Object this$protocol = this.getProtocol();
                                                    Object other$protocol = other.getProtocol();
                                                    if (this$protocol == null ? other$protocol == null : this$protocol.equals(other$protocol)) {
                                                        Object this$regionCode = this.getRegionCode();
                                                        Object other$regionCode = other.getRegionCode();
                                                        if (this$regionCode == null ? other$regionCode == null : this$regionCode.equals(other$regionCode)) {
                                                            Object this$status = this.getStatus();
                                                            Object other$status = other.getStatus();
                                                            if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                                                                Object this$lastRunAt = this.getLastRunAt();
                                                                Object other$lastRunAt = other.getLastRunAt();
                                                                if (this$lastRunAt == null ? other$lastRunAt == null : this$lastRunAt.equals(other$lastRunAt)) {
                                                                    Object this$createdBy = this.getCreatedBy();
                                                                    Object other$createdBy = other.getCreatedBy();
                                                                    if (this$createdBy == null
                                                                        ? other$createdBy == null
                                                                        : this$createdBy.equals(other$createdBy)) {
                                                                        Object this$createdAt = this.getCreatedAt();
                                                                        Object other$createdAt = other.getCreatedAt();
                                                                        return this$createdAt == null
                                                                            ? other$createdAt == null
                                                                            : this$createdAt.equals(other$createdAt);
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
        return other instanceof DiscoverTaskEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $credentialId = this.getCredentialId();
        result = result * 59 + ($credentialId == null ? 43 : $credentialId.hashCode());
        Object $totalIps = this.getTotalIps();
        result = result * 59 + ($totalIps == null ? 43 : $totalIps.hashCode());
        Object $scannedIps = this.getScannedIps();
        result = result * 59 + ($scannedIps == null ? 43 : $scannedIps.hashCode());
        Object $foundCount = this.getFoundCount();
        result = result * 59 + ($foundCount == null ? 43 : $foundCount.hashCode());
        Object $matchedCount = this.getMatchedCount();
        result = result * 59 + ($matchedCount == null ? 43 : $matchedCount.hashCode());
        Object $pendingCount = this.getPendingCount();
        result = result * 59 + ($pendingCount == null ? 43 : $pendingCount.hashCode());
        Object $taskNo = this.getTaskNo();
        result = result * 59 + ($taskNo == null ? 43 : $taskNo.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $cidr = this.getCidr();
        result = result * 59 + ($cidr == null ? 43 : $cidr.hashCode());
        Object $protocol = this.getProtocol();
        result = result * 59 + ($protocol == null ? 43 : $protocol.hashCode());
        Object $regionCode = this.getRegionCode();
        result = result * 59 + ($regionCode == null ? 43 : $regionCode.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $lastRunAt = this.getLastRunAt();
        result = result * 59 + ($lastRunAt == null ? 43 : $lastRunAt.hashCode());
        Object $createdBy = this.getCreatedBy();
        result = result * 59 + ($createdBy == null ? 43 : $createdBy.hashCode());
        Object $createdAt = this.getCreatedAt();
        return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "DiscoverTaskEntity(id="
            + this.getId()
            + ", taskNo="
            + this.getTaskNo()
            + ", name="
            + this.getName()
            + ", cidr="
            + this.getCidr()
            + ", protocol="
            + this.getProtocol()
            + ", credentialId="
            + this.getCredentialId()
            + ", regionCode="
            + this.getRegionCode()
            + ", status="
            + this.getStatus()
            + ", totalIps="
            + this.getTotalIps()
            + ", scannedIps="
            + this.getScannedIps()
            + ", foundCount="
            + this.getFoundCount()
            + ", matchedCount="
            + this.getMatchedCount()
            + ", pendingCount="
            + this.getPendingCount()
            + ", lastRunAt="
            + this.getLastRunAt()
            + ", createdBy="
            + this.getCreatedBy()
            + ", createdAt="
            + this.getCreatedAt()
            + ")";
    }
}
