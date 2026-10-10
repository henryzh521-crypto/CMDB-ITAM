package com.dcim.room.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName(
    value = "machine_room",
    autoResultMap = true
)
public class MachineRoomEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String roomCode;
    private String name;
    private String dataCenter;
    private String regionCode;
    private String city;
    private String location;
    private BigDecimal areaSqm;
    private BigDecimal loadKg;
    private BigDecimal coolingKw;
    private BigDecimal powerKw;
    private Integer cabinetCount;
    private String abPath;
    private String envMonitor;
    private String manager;
    private String remark;
    private LocalDateTime createdAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getRoomCode() {
        return this.roomCode;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getDataCenter() {
        return this.dataCenter;
    }

    
    public String getRegionCode() {
        return this.regionCode;
    }

    
    public String getCity() {
        return this.city;
    }

    
    public String getLocation() {
        return this.location;
    }

    
    public BigDecimal getAreaSqm() {
        return this.areaSqm;
    }

    
    public BigDecimal getLoadKg() {
        return this.loadKg;
    }

    
    public BigDecimal getCoolingKw() {
        return this.coolingKw;
    }

    
    public BigDecimal getPowerKw() {
        return this.powerKw;
    }

    
    public Integer getCabinetCount() {
        return this.cabinetCount;
    }

    
    public String getAbPath() {
        return this.abPath;
    }

    
    public String getEnvMonitor() {
        return this.envMonitor;
    }

    
    public String getManager() {
        return this.manager;
    }

    
    public String getRemark() {
        return this.remark;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setRoomCode(final String roomCode) {
        this.roomCode = roomCode;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setDataCenter(final String dataCenter) {
        this.dataCenter = dataCenter;
    }

    
    public void setRegionCode(final String regionCode) {
        this.regionCode = regionCode;
    }

    
    public void setCity(final String city) {
        this.city = city;
    }

    
    public void setLocation(final String location) {
        this.location = location;
    }

    
    public void setAreaSqm(final BigDecimal areaSqm) {
        this.areaSqm = areaSqm;
    }

    
    public void setLoadKg(final BigDecimal loadKg) {
        this.loadKg = loadKg;
    }

    
    public void setCoolingKw(final BigDecimal coolingKw) {
        this.coolingKw = coolingKw;
    }

    
    public void setPowerKw(final BigDecimal powerKw) {
        this.powerKw = powerKw;
    }

    
    public void setCabinetCount(final Integer cabinetCount) {
        this.cabinetCount = cabinetCount;
    }

    
    public void setAbPath(final String abPath) {
        this.abPath = abPath;
    }

    
    public void setEnvMonitor(final String envMonitor) {
        this.envMonitor = envMonitor;
    }

    
    public void setManager(final String manager) {
        this.manager = manager;
    }

    
    public void setRemark(final String remark) {
        this.remark = remark;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof MachineRoomEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$cabinetCount = this.getCabinetCount();
                Object other$cabinetCount = other.getCabinetCount();
                if (this$cabinetCount == null ? other$cabinetCount == null : this$cabinetCount.equals(other$cabinetCount)) {
                    Object this$roomCode = this.getRoomCode();
                    Object other$roomCode = other.getRoomCode();
                    if (this$roomCode == null ? other$roomCode == null : this$roomCode.equals(other$roomCode)) {
                        Object this$name = this.getName();
                        Object other$name = other.getName();
                        if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                            Object this$dataCenter = this.getDataCenter();
                            Object other$dataCenter = other.getDataCenter();
                            if (this$dataCenter == null ? other$dataCenter == null : this$dataCenter.equals(other$dataCenter)) {
                                Object this$regionCode = this.getRegionCode();
                                Object other$regionCode = other.getRegionCode();
                                if (this$regionCode == null ? other$regionCode == null : this$regionCode.equals(other$regionCode)) {
                                    Object this$city = this.getCity();
                                    Object other$city = other.getCity();
                                    if (this$city == null ? other$city == null : this$city.equals(other$city)) {
                                        Object this$location = this.getLocation();
                                        Object other$location = other.getLocation();
                                        if (this$location == null ? other$location == null : this$location.equals(other$location)) {
                                            Object this$areaSqm = this.getAreaSqm();
                                            Object other$areaSqm = other.getAreaSqm();
                                            if (this$areaSqm == null ? other$areaSqm == null : this$areaSqm.equals(other$areaSqm)) {
                                                Object this$loadKg = this.getLoadKg();
                                                Object other$loadKg = other.getLoadKg();
                                                if (this$loadKg == null ? other$loadKg == null : this$loadKg.equals(other$loadKg)) {
                                                    Object this$coolingKw = this.getCoolingKw();
                                                    Object other$coolingKw = other.getCoolingKw();
                                                    if (this$coolingKw == null ? other$coolingKw == null : this$coolingKw.equals(other$coolingKw)) {
                                                        Object this$powerKw = this.getPowerKw();
                                                        Object other$powerKw = other.getPowerKw();
                                                        if (this$powerKw == null ? other$powerKw == null : this$powerKw.equals(other$powerKw)) {
                                                            Object this$abPath = this.getAbPath();
                                                            Object other$abPath = other.getAbPath();
                                                            if (this$abPath == null ? other$abPath == null : this$abPath.equals(other$abPath)) {
                                                                Object this$envMonitor = this.getEnvMonitor();
                                                                Object other$envMonitor = other.getEnvMonitor();
                                                                if (this$envMonitor == null
                                                                    ? other$envMonitor == null
                                                                    : this$envMonitor.equals(other$envMonitor)) {
                                                                    Object this$manager = this.getManager();
                                                                    Object other$manager = other.getManager();
                                                                    if (this$manager == null ? other$manager == null : this$manager.equals(other$manager)) {
                                                                        Object this$remark = this.getRemark();
                                                                        Object other$remark = other.getRemark();
                                                                        if (this$remark == null ? other$remark == null : this$remark.equals(other$remark)) {
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
            } else {
                return false;
            }
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof MachineRoomEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $cabinetCount = this.getCabinetCount();
        result = result * 59 + ($cabinetCount == null ? 43 : $cabinetCount.hashCode());
        Object $roomCode = this.getRoomCode();
        result = result * 59 + ($roomCode == null ? 43 : $roomCode.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $dataCenter = this.getDataCenter();
        result = result * 59 + ($dataCenter == null ? 43 : $dataCenter.hashCode());
        Object $regionCode = this.getRegionCode();
        result = result * 59 + ($regionCode == null ? 43 : $regionCode.hashCode());
        Object $city = this.getCity();
        result = result * 59 + ($city == null ? 43 : $city.hashCode());
        Object $location = this.getLocation();
        result = result * 59 + ($location == null ? 43 : $location.hashCode());
        Object $areaSqm = this.getAreaSqm();
        result = result * 59 + ($areaSqm == null ? 43 : $areaSqm.hashCode());
        Object $loadKg = this.getLoadKg();
        result = result * 59 + ($loadKg == null ? 43 : $loadKg.hashCode());
        Object $coolingKw = this.getCoolingKw();
        result = result * 59 + ($coolingKw == null ? 43 : $coolingKw.hashCode());
        Object $powerKw = this.getPowerKw();
        result = result * 59 + ($powerKw == null ? 43 : $powerKw.hashCode());
        Object $abPath = this.getAbPath();
        result = result * 59 + ($abPath == null ? 43 : $abPath.hashCode());
        Object $envMonitor = this.getEnvMonitor();
        result = result * 59 + ($envMonitor == null ? 43 : $envMonitor.hashCode());
        Object $manager = this.getManager();
        result = result * 59 + ($manager == null ? 43 : $manager.hashCode());
        Object $remark = this.getRemark();
        result = result * 59 + ($remark == null ? 43 : $remark.hashCode());
        Object $createdAt = this.getCreatedAt();
        return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "MachineRoomEntity(id="
            + this.getId()
            + ", roomCode="
            + this.getRoomCode()
            + ", name="
            + this.getName()
            + ", dataCenter="
            + this.getDataCenter()
            + ", regionCode="
            + this.getRegionCode()
            + ", city="
            + this.getCity()
            + ", location="
            + this.getLocation()
            + ", areaSqm="
            + this.getAreaSqm()
            + ", loadKg="
            + this.getLoadKg()
            + ", coolingKw="
            + this.getCoolingKw()
            + ", powerKw="
            + this.getPowerKw()
            + ", cabinetCount="
            + this.getCabinetCount()
            + ", abPath="
            + this.getAbPath()
            + ", envMonitor="
            + this.getEnvMonitor()
            + ", manager="
            + this.getManager()
            + ", remark="
            + this.getRemark()
            + ", createdAt="
            + this.getCreatedAt()
            + ")";
    }
}
