package com.dcim.gpu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.JsonListTypeHandler;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@TableName(
    value = "gpu_card",
    autoResultMap = true
)
public class GpuCardEntity {
    public static final String LV_GOOD = "优";
    public static final String LV_FINE = "良";
    public static final String LV_WATCH = "观察";
    public static final String LV_DEGRADED = "劣化";
    public static final String LV_CRITICAL = "高危";
    @TableId(
        type = IdType.INPUT
    )
    private String cardNo;
    private String hostAssetNo;
    private Integer slot;
    private String gpuSn;
    private String model;
    private Integer memoryGb;
    private String fw;
    private String batchNo;
    private Boolean migCapable;
    private Integer healthScore;
    private String healthLevel;
    private String prevSn;
    @TableField(
        typeHandler = JsonListTypeHandler.class
    )
    private List<Object> swapHistory = new ArrayList<>();
    private LocalDateTime lastScoredAt;
    private LocalDateTime updatedAt;

    
    public String getCardNo() {
        return this.cardNo;
    }

    
    public String getHostAssetNo() {
        return this.hostAssetNo;
    }

    
    public Integer getSlot() {
        return this.slot;
    }

    
    public String getGpuSn() {
        return this.gpuSn;
    }

    
    public String getModel() {
        return this.model;
    }

    
    public Integer getMemoryGb() {
        return this.memoryGb;
    }

    
    public String getFw() {
        return this.fw;
    }

    
    public String getBatchNo() {
        return this.batchNo;
    }

    
    public Boolean getMigCapable() {
        return this.migCapable;
    }

    
    public Integer getHealthScore() {
        return this.healthScore;
    }

    
    public String getHealthLevel() {
        return this.healthLevel;
    }

    
    public String getPrevSn() {
        return this.prevSn;
    }

    
    public List<Object> getSwapHistory() {
        return this.swapHistory;
    }

    
    public LocalDateTime getLastScoredAt() {
        return this.lastScoredAt;
    }

    
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    
    public void setCardNo(final String cardNo) {
        this.cardNo = cardNo;
    }

    
    public void setHostAssetNo(final String hostAssetNo) {
        this.hostAssetNo = hostAssetNo;
    }

    
    public void setSlot(final Integer slot) {
        this.slot = slot;
    }

    
    public void setGpuSn(final String gpuSn) {
        this.gpuSn = gpuSn;
    }

    
    public void setModel(final String model) {
        this.model = model;
    }

    
    public void setMemoryGb(final Integer memoryGb) {
        this.memoryGb = memoryGb;
    }

    
    public void setFw(final String fw) {
        this.fw = fw;
    }

    
    public void setBatchNo(final String batchNo) {
        this.batchNo = batchNo;
    }

    
    public void setMigCapable(final Boolean migCapable) {
        this.migCapable = migCapable;
    }

    
    public void setHealthScore(final Integer healthScore) {
        this.healthScore = healthScore;
    }

    
    public void setHealthLevel(final String healthLevel) {
        this.healthLevel = healthLevel;
    }

    
    public void setPrevSn(final String prevSn) {
        this.prevSn = prevSn;
    }

    
    public void setSwapHistory(final List<Object> swapHistory) {
        this.swapHistory = swapHistory;
    }

    
    public void setLastScoredAt(final LocalDateTime lastScoredAt) {
        this.lastScoredAt = lastScoredAt;
    }

    
    public void setUpdatedAt(final LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof GpuCardEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$slot = this.getSlot();
            Object other$slot = other.getSlot();
            if (this$slot == null ? other$slot == null : this$slot.equals(other$slot)) {
                Object this$memoryGb = this.getMemoryGb();
                Object other$memoryGb = other.getMemoryGb();
                if (this$memoryGb == null ? other$memoryGb == null : this$memoryGb.equals(other$memoryGb)) {
                    Object this$migCapable = this.getMigCapable();
                    Object other$migCapable = other.getMigCapable();
                    if (this$migCapable == null ? other$migCapable == null : this$migCapable.equals(other$migCapable)) {
                        Object this$healthScore = this.getHealthScore();
                        Object other$healthScore = other.getHealthScore();
                        if (this$healthScore == null ? other$healthScore == null : this$healthScore.equals(other$healthScore)) {
                            Object this$cardNo = this.getCardNo();
                            Object other$cardNo = other.getCardNo();
                            if (this$cardNo == null ? other$cardNo == null : this$cardNo.equals(other$cardNo)) {
                                Object this$hostAssetNo = this.getHostAssetNo();
                                Object other$hostAssetNo = other.getHostAssetNo();
                                if (this$hostAssetNo == null ? other$hostAssetNo == null : this$hostAssetNo.equals(other$hostAssetNo)) {
                                    Object this$gpuSn = this.getGpuSn();
                                    Object other$gpuSn = other.getGpuSn();
                                    if (this$gpuSn == null ? other$gpuSn == null : this$gpuSn.equals(other$gpuSn)) {
                                        Object this$model = this.getModel();
                                        Object other$model = other.getModel();
                                        if (this$model == null ? other$model == null : this$model.equals(other$model)) {
                                            Object this$fw = this.getFw();
                                            Object other$fw = other.getFw();
                                            if (this$fw == null ? other$fw == null : this$fw.equals(other$fw)) {
                                                Object this$batchNo = this.getBatchNo();
                                                Object other$batchNo = other.getBatchNo();
                                                if (this$batchNo == null ? other$batchNo == null : this$batchNo.equals(other$batchNo)) {
                                                    Object this$healthLevel = this.getHealthLevel();
                                                    Object other$healthLevel = other.getHealthLevel();
                                                    if (this$healthLevel == null ? other$healthLevel == null : this$healthLevel.equals(other$healthLevel)) {
                                                        Object this$prevSn = this.getPrevSn();
                                                        Object other$prevSn = other.getPrevSn();
                                                        if (this$prevSn == null ? other$prevSn == null : this$prevSn.equals(other$prevSn)) {
                                                            Object this$swapHistory = this.getSwapHistory();
                                                            Object other$swapHistory = other.getSwapHistory();
                                                            if (this$swapHistory == null
                                                                ? other$swapHistory == null
                                                                : this$swapHistory.equals(other$swapHistory)) {
                                                                Object this$lastScoredAt = this.getLastScoredAt();
                                                                Object other$lastScoredAt = other.getLastScoredAt();
                                                                if (this$lastScoredAt == null
                                                                    ? other$lastScoredAt == null
                                                                    : this$lastScoredAt.equals(other$lastScoredAt)) {
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
        return other instanceof GpuCardEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $slot = this.getSlot();
        result = result * 59 + ($slot == null ? 43 : $slot.hashCode());
        Object $memoryGb = this.getMemoryGb();
        result = result * 59 + ($memoryGb == null ? 43 : $memoryGb.hashCode());
        Object $migCapable = this.getMigCapable();
        result = result * 59 + ($migCapable == null ? 43 : $migCapable.hashCode());
        Object $healthScore = this.getHealthScore();
        result = result * 59 + ($healthScore == null ? 43 : $healthScore.hashCode());
        Object $cardNo = this.getCardNo();
        result = result * 59 + ($cardNo == null ? 43 : $cardNo.hashCode());
        Object $hostAssetNo = this.getHostAssetNo();
        result = result * 59 + ($hostAssetNo == null ? 43 : $hostAssetNo.hashCode());
        Object $gpuSn = this.getGpuSn();
        result = result * 59 + ($gpuSn == null ? 43 : $gpuSn.hashCode());
        Object $model = this.getModel();
        result = result * 59 + ($model == null ? 43 : $model.hashCode());
        Object $fw = this.getFw();
        result = result * 59 + ($fw == null ? 43 : $fw.hashCode());
        Object $batchNo = this.getBatchNo();
        result = result * 59 + ($batchNo == null ? 43 : $batchNo.hashCode());
        Object $healthLevel = this.getHealthLevel();
        result = result * 59 + ($healthLevel == null ? 43 : $healthLevel.hashCode());
        Object $prevSn = this.getPrevSn();
        result = result * 59 + ($prevSn == null ? 43 : $prevSn.hashCode());
        Object $swapHistory = this.getSwapHistory();
        result = result * 59 + ($swapHistory == null ? 43 : $swapHistory.hashCode());
        Object $lastScoredAt = this.getLastScoredAt();
        result = result * 59 + ($lastScoredAt == null ? 43 : $lastScoredAt.hashCode());
        Object $updatedAt = this.getUpdatedAt();
        return result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "GpuCardEntity(cardNo="
            + this.getCardNo()
            + ", hostAssetNo="
            + this.getHostAssetNo()
            + ", slot="
            + this.getSlot()
            + ", gpuSn="
            + this.getGpuSn()
            + ", model="
            + this.getModel()
            + ", memoryGb="
            + this.getMemoryGb()
            + ", fw="
            + this.getFw()
            + ", batchNo="
            + this.getBatchNo()
            + ", migCapable="
            + this.getMigCapable()
            + ", healthScore="
            + this.getHealthScore()
            + ", healthLevel="
            + this.getHealthLevel()
            + ", prevSn="
            + this.getPrevSn()
            + ", swapHistory="
            + this.getSwapHistory()
            + ", lastScoredAt="
            + this.getLastScoredAt()
            + ", updatedAt="
            + this.getUpdatedAt()
            + ")";
    }
}
