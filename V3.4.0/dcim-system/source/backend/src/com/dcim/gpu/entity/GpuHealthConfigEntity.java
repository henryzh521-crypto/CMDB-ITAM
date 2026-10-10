package com.dcim.gpu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gpu_health_config")
public class GpuHealthConfigEntity {
    @TableId(
        type = IdType.INPUT
    )
    private Integer id;
    private Integer weightEcc;
    private Integer weightTemp;
    private Integer weightXid;
    private Integer weightPower;
    private Integer tempLimitC;
    private LocalDateTime updatedAt;

    
    public Integer getId() {
        return this.id;
    }

    
    public Integer getWeightEcc() {
        return this.weightEcc;
    }

    
    public Integer getWeightTemp() {
        return this.weightTemp;
    }

    
    public Integer getWeightXid() {
        return this.weightXid;
    }

    
    public Integer getWeightPower() {
        return this.weightPower;
    }

    
    public Integer getTempLimitC() {
        return this.tempLimitC;
    }

    
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    
    public void setId(final Integer id) {
        this.id = id;
    }

    
    public void setWeightEcc(final Integer weightEcc) {
        this.weightEcc = weightEcc;
    }

    
    public void setWeightTemp(final Integer weightTemp) {
        this.weightTemp = weightTemp;
    }

    
    public void setWeightXid(final Integer weightXid) {
        this.weightXid = weightXid;
    }

    
    public void setWeightPower(final Integer weightPower) {
        this.weightPower = weightPower;
    }

    
    public void setTempLimitC(final Integer tempLimitC) {
        this.tempLimitC = tempLimitC;
    }

    
    public void setUpdatedAt(final LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof GpuHealthConfigEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$weightEcc = this.getWeightEcc();
                Object other$weightEcc = other.getWeightEcc();
                if (this$weightEcc == null ? other$weightEcc == null : this$weightEcc.equals(other$weightEcc)) {
                    Object this$weightTemp = this.getWeightTemp();
                    Object other$weightTemp = other.getWeightTemp();
                    if (this$weightTemp == null ? other$weightTemp == null : this$weightTemp.equals(other$weightTemp)) {
                        Object this$weightXid = this.getWeightXid();
                        Object other$weightXid = other.getWeightXid();
                        if (this$weightXid == null ? other$weightXid == null : this$weightXid.equals(other$weightXid)) {
                            Object this$weightPower = this.getWeightPower();
                            Object other$weightPower = other.getWeightPower();
                            if (this$weightPower == null ? other$weightPower == null : this$weightPower.equals(other$weightPower)) {
                                Object this$tempLimitC = this.getTempLimitC();
                                Object other$tempLimitC = other.getTempLimitC();
                                if (this$tempLimitC == null ? other$tempLimitC == null : this$tempLimitC.equals(other$tempLimitC)) {
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
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof GpuHealthConfigEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $weightEcc = this.getWeightEcc();
        result = result * 59 + ($weightEcc == null ? 43 : $weightEcc.hashCode());
        Object $weightTemp = this.getWeightTemp();
        result = result * 59 + ($weightTemp == null ? 43 : $weightTemp.hashCode());
        Object $weightXid = this.getWeightXid();
        result = result * 59 + ($weightXid == null ? 43 : $weightXid.hashCode());
        Object $weightPower = this.getWeightPower();
        result = result * 59 + ($weightPower == null ? 43 : $weightPower.hashCode());
        Object $tempLimitC = this.getTempLimitC();
        result = result * 59 + ($tempLimitC == null ? 43 : $tempLimitC.hashCode());
        Object $updatedAt = this.getUpdatedAt();
        return result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "GpuHealthConfigEntity(id="
            + this.getId()
            + ", weightEcc="
            + this.getWeightEcc()
            + ", weightTemp="
            + this.getWeightTemp()
            + ", weightXid="
            + this.getWeightXid()
            + ", weightPower="
            + this.getWeightPower()
            + ", tempLimitC="
            + this.getTempLimitC()
            + ", updatedAt="
            + this.getUpdatedAt()
            + ")";
    }
}
