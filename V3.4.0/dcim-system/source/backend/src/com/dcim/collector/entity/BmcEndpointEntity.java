package com.dcim.collector.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("bmc_endpoint")
public class BmcEndpointEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String bmcIp;
    private String mac;
    private String assetNo;
    private Long credentialId;
    private String redfishVersion;
    private String ipmiVersion;
    private Integer collectIntervalMin;
    private Integer consecutiveFailures;
    private LocalDateTime lastCollectAt;
    private Boolean lastCollectOk;
    private LocalDateTime nextCollectAt;
    private Boolean disabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getBmcIp() {
        return this.bmcIp;
    }

    
    public String getMac() {
        return this.mac;
    }

    
    public String getAssetNo() {
        return this.assetNo;
    }

    
    public Long getCredentialId() {
        return this.credentialId;
    }

    
    public String getRedfishVersion() {
        return this.redfishVersion;
    }

    
    public String getIpmiVersion() {
        return this.ipmiVersion;
    }

    
    public Integer getCollectIntervalMin() {
        return this.collectIntervalMin;
    }

    
    public Integer getConsecutiveFailures() {
        return this.consecutiveFailures;
    }

    
    public LocalDateTime getLastCollectAt() {
        return this.lastCollectAt;
    }

    
    public Boolean getLastCollectOk() {
        return this.lastCollectOk;
    }

    
    public LocalDateTime getNextCollectAt() {
        return this.nextCollectAt;
    }

    
    public Boolean getDisabled() {
        return this.disabled;
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

    
    public void setBmcIp(final String bmcIp) {
        this.bmcIp = bmcIp;
    }

    
    public void setMac(final String mac) {
        this.mac = mac;
    }

    
    public void setAssetNo(final String assetNo) {
        this.assetNo = assetNo;
    }

    
    public void setCredentialId(final Long credentialId) {
        this.credentialId = credentialId;
    }

    
    public void setRedfishVersion(final String redfishVersion) {
        this.redfishVersion = redfishVersion;
    }

    
    public void setIpmiVersion(final String ipmiVersion) {
        this.ipmiVersion = ipmiVersion;
    }

    
    public void setCollectIntervalMin(final Integer collectIntervalMin) {
        this.collectIntervalMin = collectIntervalMin;
    }

    
    public void setConsecutiveFailures(final Integer consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    
    public void setLastCollectAt(final LocalDateTime lastCollectAt) {
        this.lastCollectAt = lastCollectAt;
    }

    
    public void setLastCollectOk(final Boolean lastCollectOk) {
        this.lastCollectOk = lastCollectOk;
    }

    
    public void setNextCollectAt(final LocalDateTime nextCollectAt) {
        this.nextCollectAt = nextCollectAt;
    }

    
    public void setDisabled(final Boolean disabled) {
        this.disabled = disabled;
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
        } else if (!(o instanceof BmcEndpointEntity other)) {
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
                    Object this$collectIntervalMin = this.getCollectIntervalMin();
                    Object other$collectIntervalMin = other.getCollectIntervalMin();
                    if (this$collectIntervalMin == null ? other$collectIntervalMin == null : this$collectIntervalMin.equals(other$collectIntervalMin)) {
                        Object this$consecutiveFailures = this.getConsecutiveFailures();
                        Object other$consecutiveFailures = other.getConsecutiveFailures();
                        if (this$consecutiveFailures == null ? other$consecutiveFailures == null : this$consecutiveFailures.equals(other$consecutiveFailures)) {
                            Object this$lastCollectOk = this.getLastCollectOk();
                            Object other$lastCollectOk = other.getLastCollectOk();
                            if (this$lastCollectOk == null ? other$lastCollectOk == null : this$lastCollectOk.equals(other$lastCollectOk)) {
                                Object this$disabled = this.getDisabled();
                                Object other$disabled = other.getDisabled();
                                if (this$disabled == null ? other$disabled == null : this$disabled.equals(other$disabled)) {
                                    Object this$bmcIp = this.getBmcIp();
                                    Object other$bmcIp = other.getBmcIp();
                                    if (this$bmcIp == null ? other$bmcIp == null : this$bmcIp.equals(other$bmcIp)) {
                                        Object this$mac = this.getMac();
                                        Object other$mac = other.getMac();
                                        if (this$mac == null ? other$mac == null : this$mac.equals(other$mac)) {
                                            Object this$assetNo = this.getAssetNo();
                                            Object other$assetNo = other.getAssetNo();
                                            if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                                                Object this$redfishVersion = this.getRedfishVersion();
                                                Object other$redfishVersion = other.getRedfishVersion();
                                                if (this$redfishVersion == null
                                                    ? other$redfishVersion == null
                                                    : this$redfishVersion.equals(other$redfishVersion)) {
                                                    Object this$ipmiVersion = this.getIpmiVersion();
                                                    Object other$ipmiVersion = other.getIpmiVersion();
                                                    if (this$ipmiVersion == null ? other$ipmiVersion == null : this$ipmiVersion.equals(other$ipmiVersion)) {
                                                        Object this$lastCollectAt = this.getLastCollectAt();
                                                        Object other$lastCollectAt = other.getLastCollectAt();
                                                        if (this$lastCollectAt == null
                                                            ? other$lastCollectAt == null
                                                            : this$lastCollectAt.equals(other$lastCollectAt)) {
                                                            Object this$nextCollectAt = this.getNextCollectAt();
                                                            Object other$nextCollectAt = other.getNextCollectAt();
                                                            if (this$nextCollectAt == null
                                                                ? other$nextCollectAt == null
                                                                : this$nextCollectAt.equals(other$nextCollectAt)) {
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
        return other instanceof BmcEndpointEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $credentialId = this.getCredentialId();
        result = result * 59 + ($credentialId == null ? 43 : $credentialId.hashCode());
        Object $collectIntervalMin = this.getCollectIntervalMin();
        result = result * 59 + ($collectIntervalMin == null ? 43 : $collectIntervalMin.hashCode());
        Object $consecutiveFailures = this.getConsecutiveFailures();
        result = result * 59 + ($consecutiveFailures == null ? 43 : $consecutiveFailures.hashCode());
        Object $lastCollectOk = this.getLastCollectOk();
        result = result * 59 + ($lastCollectOk == null ? 43 : $lastCollectOk.hashCode());
        Object $disabled = this.getDisabled();
        result = result * 59 + ($disabled == null ? 43 : $disabled.hashCode());
        Object $bmcIp = this.getBmcIp();
        result = result * 59 + ($bmcIp == null ? 43 : $bmcIp.hashCode());
        Object $mac = this.getMac();
        result = result * 59 + ($mac == null ? 43 : $mac.hashCode());
        Object $assetNo = this.getAssetNo();
        result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
        Object $redfishVersion = this.getRedfishVersion();
        result = result * 59 + ($redfishVersion == null ? 43 : $redfishVersion.hashCode());
        Object $ipmiVersion = this.getIpmiVersion();
        result = result * 59 + ($ipmiVersion == null ? 43 : $ipmiVersion.hashCode());
        Object $lastCollectAt = this.getLastCollectAt();
        result = result * 59 + ($lastCollectAt == null ? 43 : $lastCollectAt.hashCode());
        Object $nextCollectAt = this.getNextCollectAt();
        result = result * 59 + ($nextCollectAt == null ? 43 : $nextCollectAt.hashCode());
        Object $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
        Object $updatedAt = this.getUpdatedAt();
        return result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "BmcEndpointEntity(id="
            + this.getId()
            + ", bmcIp="
            + this.getBmcIp()
            + ", mac="
            + this.getMac()
            + ", assetNo="
            + this.getAssetNo()
            + ", credentialId="
            + this.getCredentialId()
            + ", redfishVersion="
            + this.getRedfishVersion()
            + ", ipmiVersion="
            + this.getIpmiVersion()
            + ", collectIntervalMin="
            + this.getCollectIntervalMin()
            + ", consecutiveFailures="
            + this.getConsecutiveFailures()
            + ", lastCollectAt="
            + this.getLastCollectAt()
            + ", lastCollectOk="
            + this.getLastCollectOk()
            + ", nextCollectAt="
            + this.getNextCollectAt()
            + ", disabled="
            + this.getDisabled()
            + ", createdAt="
            + this.getCreatedAt()
            + ", updatedAt="
            + this.getUpdatedAt()
            + ")";
    }
}
