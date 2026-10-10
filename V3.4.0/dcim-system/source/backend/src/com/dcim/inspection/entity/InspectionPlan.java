package com.dcim.inspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.JsonListTypeHandler;
import com.dcim.common.StringArrayTypeHandler;
import java.time.LocalDateTime;
import java.util.List;

@TableName(
    value = "inspection_plan",
    autoResultMap = true
)
public class InspectionPlan {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String planNo;
    private String name;
    private String scopeType;
    @TableField(
        typeHandler = StringArrayTypeHandler.class
    )
    private List<String> scopeCodes;
    private String frequency;
    private Integer runHour;
    private Integer runWeekday;
    private Integer runMonthDay;
    @TableField(
        typeHandler = JsonListTypeHandler.class
    )
    private List<Object> checklist;
    private String priority;
    private String assignee;
    private Boolean enabled;
    private LocalDateTime nextRunAt;
    private LocalDateTime lastRunAt;
    private String lastWoNo;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getPlanNo() {
        return this.planNo;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getScopeType() {
        return this.scopeType;
    }

    
    public List<String> getScopeCodes() {
        return this.scopeCodes;
    }

    
    public String getFrequency() {
        return this.frequency;
    }

    
    public Integer getRunHour() {
        return this.runHour;
    }

    
    public Integer getRunWeekday() {
        return this.runWeekday;
    }

    
    public Integer getRunMonthDay() {
        return this.runMonthDay;
    }

    
    public List<Object> getChecklist() {
        return this.checklist;
    }

    
    public String getPriority() {
        return this.priority;
    }

    
    public String getAssignee() {
        return this.assignee;
    }

    
    public Boolean getEnabled() {
        return this.enabled;
    }

    
    public LocalDateTime getNextRunAt() {
        return this.nextRunAt;
    }

    
    public LocalDateTime getLastRunAt() {
        return this.lastRunAt;
    }

    
    public String getLastWoNo() {
        return this.lastWoNo;
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

    
    public void setPlanNo(final String planNo) {
        this.planNo = planNo;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setScopeType(final String scopeType) {
        this.scopeType = scopeType;
    }

    
    public void setScopeCodes(final List<String> scopeCodes) {
        this.scopeCodes = scopeCodes;
    }

    
    public void setFrequency(final String frequency) {
        this.frequency = frequency;
    }

    
    public void setRunHour(final Integer runHour) {
        this.runHour = runHour;
    }

    
    public void setRunWeekday(final Integer runWeekday) {
        this.runWeekday = runWeekday;
    }

    
    public void setRunMonthDay(final Integer runMonthDay) {
        this.runMonthDay = runMonthDay;
    }

    
    public void setChecklist(final List<Object> checklist) {
        this.checklist = checklist;
    }

    
    public void setPriority(final String priority) {
        this.priority = priority;
    }

    
    public void setAssignee(final String assignee) {
        this.assignee = assignee;
    }

    
    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }

    
    public void setNextRunAt(final LocalDateTime nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    
    public void setLastRunAt(final LocalDateTime lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    
    public void setLastWoNo(final String lastWoNo) {
        this.lastWoNo = lastWoNo;
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
        } else if (!(o instanceof InspectionPlan other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$runHour = this.getRunHour();
                Object other$runHour = other.getRunHour();
                if (this$runHour == null ? other$runHour == null : this$runHour.equals(other$runHour)) {
                    Object this$runWeekday = this.getRunWeekday();
                    Object other$runWeekday = other.getRunWeekday();
                    if (this$runWeekday == null ? other$runWeekday == null : this$runWeekday.equals(other$runWeekday)) {
                        Object this$runMonthDay = this.getRunMonthDay();
                        Object other$runMonthDay = other.getRunMonthDay();
                        if (this$runMonthDay == null ? other$runMonthDay == null : this$runMonthDay.equals(other$runMonthDay)) {
                            Object this$enabled = this.getEnabled();
                            Object other$enabled = other.getEnabled();
                            if (this$enabled == null ? other$enabled == null : this$enabled.equals(other$enabled)) {
                                Object this$planNo = this.getPlanNo();
                                Object other$planNo = other.getPlanNo();
                                if (this$planNo == null ? other$planNo == null : this$planNo.equals(other$planNo)) {
                                    Object this$name = this.getName();
                                    Object other$name = other.getName();
                                    if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                                        Object this$scopeType = this.getScopeType();
                                        Object other$scopeType = other.getScopeType();
                                        if (this$scopeType == null ? other$scopeType == null : this$scopeType.equals(other$scopeType)) {
                                            Object this$scopeCodes = this.getScopeCodes();
                                            Object other$scopeCodes = other.getScopeCodes();
                                            if (this$scopeCodes == null ? other$scopeCodes == null : this$scopeCodes.equals(other$scopeCodes)) {
                                                Object this$frequency = this.getFrequency();
                                                Object other$frequency = other.getFrequency();
                                                if (this$frequency == null ? other$frequency == null : this$frequency.equals(other$frequency)) {
                                                    Object this$checklist = this.getChecklist();
                                                    Object other$checklist = other.getChecklist();
                                                    if (this$checklist == null ? other$checklist == null : this$checklist.equals(other$checklist)) {
                                                        Object this$priority = this.getPriority();
                                                        Object other$priority = other.getPriority();
                                                        if (this$priority == null ? other$priority == null : this$priority.equals(other$priority)) {
                                                            Object this$assignee = this.getAssignee();
                                                            Object other$assignee = other.getAssignee();
                                                            if (this$assignee == null ? other$assignee == null : this$assignee.equals(other$assignee)) {
                                                                Object this$nextRunAt = this.getNextRunAt();
                                                                Object other$nextRunAt = other.getNextRunAt();
                                                                if (this$nextRunAt == null ? other$nextRunAt == null : this$nextRunAt.equals(other$nextRunAt)) {
                                                                    Object this$lastRunAt = this.getLastRunAt();
                                                                    Object other$lastRunAt = other.getLastRunAt();
                                                                    if (this$lastRunAt == null
                                                                        ? other$lastRunAt == null
                                                                        : this$lastRunAt.equals(other$lastRunAt)) {
                                                                        Object this$lastWoNo = this.getLastWoNo();
                                                                        Object other$lastWoNo = other.getLastWoNo();
                                                                        if (this$lastWoNo == null
                                                                            ? other$lastWoNo == null
                                                                            : this$lastWoNo.equals(other$lastWoNo)) {
                                                                            Object this$createdBy = this.getCreatedBy();
                                                                            Object other$createdBy = other.getCreatedBy();
                                                                            if (this$createdBy == null
                                                                                ? other$createdBy == null
                                                                                : this$createdBy.equals(other$createdBy)) {
                                                                                Object this$createdAt = this.getCreatedAt();
                                                                                Object other$createdAt = other.getCreatedAt();
                                                                                if (this$createdAt == null
                                                                                    ? other$createdAt == null
                                                                                    : this$createdAt.equals(other$createdAt)) {
                                                                                    Object this$updatedAt = this.getUpdatedAt();
                                                                                    Object other$updatedAt = other.getUpdatedAt();
                                                                                    if (this$updatedAt == null
                                                                                        ? other$updatedAt == null
                                                                                        : this$updatedAt.equals(other$updatedAt)) {
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
        return other instanceof InspectionPlan;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $runHour = this.getRunHour();
        result = result * 59 + ($runHour == null ? 43 : $runHour.hashCode());
        Object $runWeekday = this.getRunWeekday();
        result = result * 59 + ($runWeekday == null ? 43 : $runWeekday.hashCode());
        Object $runMonthDay = this.getRunMonthDay();
        result = result * 59 + ($runMonthDay == null ? 43 : $runMonthDay.hashCode());
        Object $enabled = this.getEnabled();
        result = result * 59 + ($enabled == null ? 43 : $enabled.hashCode());
        Object $planNo = this.getPlanNo();
        result = result * 59 + ($planNo == null ? 43 : $planNo.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $scopeType = this.getScopeType();
        result = result * 59 + ($scopeType == null ? 43 : $scopeType.hashCode());
        Object $scopeCodes = this.getScopeCodes();
        result = result * 59 + ($scopeCodes == null ? 43 : $scopeCodes.hashCode());
        Object $frequency = this.getFrequency();
        result = result * 59 + ($frequency == null ? 43 : $frequency.hashCode());
        Object $checklist = this.getChecklist();
        result = result * 59 + ($checklist == null ? 43 : $checklist.hashCode());
        Object $priority = this.getPriority();
        result = result * 59 + ($priority == null ? 43 : $priority.hashCode());
        Object $assignee = this.getAssignee();
        result = result * 59 + ($assignee == null ? 43 : $assignee.hashCode());
        Object $nextRunAt = this.getNextRunAt();
        result = result * 59 + ($nextRunAt == null ? 43 : $nextRunAt.hashCode());
        Object $lastRunAt = this.getLastRunAt();
        result = result * 59 + ($lastRunAt == null ? 43 : $lastRunAt.hashCode());
        Object $lastWoNo = this.getLastWoNo();
        result = result * 59 + ($lastWoNo == null ? 43 : $lastWoNo.hashCode());
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
        return "InspectionPlan(id="
            + this.getId()
            + ", planNo="
            + this.getPlanNo()
            + ", name="
            + this.getName()
            + ", scopeType="
            + this.getScopeType()
            + ", scopeCodes="
            + this.getScopeCodes()
            + ", frequency="
            + this.getFrequency()
            + ", runHour="
            + this.getRunHour()
            + ", runWeekday="
            + this.getRunWeekday()
            + ", runMonthDay="
            + this.getRunMonthDay()
            + ", checklist="
            + this.getChecklist()
            + ", priority="
            + this.getPriority()
            + ", assignee="
            + this.getAssignee()
            + ", enabled="
            + this.getEnabled()
            + ", nextRunAt="
            + this.getNextRunAt()
            + ", lastRunAt="
            + this.getLastRunAt()
            + ", lastWoNo="
            + this.getLastWoNo()
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
