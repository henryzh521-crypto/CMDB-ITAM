package com.dcim.room.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("rack_unit")
public class RackUnitEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String rackCode;
    private Integer unitNo;
    private String status;
    private String occupiedBy;

    
    public Long getId() {
        return this.id;
    }

    
    public String getRackCode() {
        return this.rackCode;
    }

    
    public Integer getUnitNo() {
        return this.unitNo;
    }

    
    public String getStatus() {
        return this.status;
    }

    
    public String getOccupiedBy() {
        return this.occupiedBy;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setRackCode(final String rackCode) {
        this.rackCode = rackCode;
    }

    
    public void setUnitNo(final Integer unitNo) {
        this.unitNo = unitNo;
    }

    
    public void setStatus(final String status) {
        this.status = status;
    }

    
    public void setOccupiedBy(final String occupiedBy) {
        this.occupiedBy = occupiedBy;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof RackUnitEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$unitNo = this.getUnitNo();
                Object other$unitNo = other.getUnitNo();
                if (this$unitNo == null ? other$unitNo == null : this$unitNo.equals(other$unitNo)) {
                    Object this$rackCode = this.getRackCode();
                    Object other$rackCode = other.getRackCode();
                    if (this$rackCode == null ? other$rackCode == null : this$rackCode.equals(other$rackCode)) {
                        Object this$status = this.getStatus();
                        Object other$status = other.getStatus();
                        if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                            Object this$occupiedBy = this.getOccupiedBy();
                            Object other$occupiedBy = other.getOccupiedBy();
                            return this$occupiedBy == null ? other$occupiedBy == null : this$occupiedBy.equals(other$occupiedBy);
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
        return other instanceof RackUnitEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $unitNo = this.getUnitNo();
        result = result * 59 + ($unitNo == null ? 43 : $unitNo.hashCode());
        Object $rackCode = this.getRackCode();
        result = result * 59 + ($rackCode == null ? 43 : $rackCode.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $occupiedBy = this.getOccupiedBy();
        return result * 59 + ($occupiedBy == null ? 43 : $occupiedBy.hashCode());
    }

    
    @Override
    public String toString() {
        return "RackUnitEntity(id="
            + this.getId()
            + ", rackCode="
            + this.getRackCode()
            + ", unitNo="
            + this.getUnitNo()
            + ", status="
            + this.getStatus()
            + ", occupiedBy="
            + this.getOccupiedBy()
            + ")";
    }
}
