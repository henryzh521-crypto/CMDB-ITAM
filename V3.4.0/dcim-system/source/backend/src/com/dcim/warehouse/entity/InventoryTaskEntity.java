package com.dcim.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.JsonbTypeHandler;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(
    value = "inventory_task",
    autoResultMap = true
)
public class InventoryTaskEntity {
    @TableId(
        type = IdType.INPUT
    )
    private String taskNo;
    private String name;
    private String scope;
    @TableField(
        typeHandler = JsonbTypeHandler.class
    )
    private Map<String, Object> filter;
    private String targetType;
    private String inspector;
    private String status;
    private Integer totalCount;
    private Integer checkedCount;
    private Integer diffCount;
    private String creator;
    private LocalDate planFinish;
    private LocalDateTime finishTime;
    private Integer version;
    private LocalDateTime createdAt;

    
    public String getTaskNo() {
        return this.taskNo;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getScope() {
        return this.scope;
    }

    
    public Map<String, Object> getFilter() {
        return this.filter;
    }

    
    public String getTargetType() {
        return this.targetType;
    }

    
    public String getInspector() {
        return this.inspector;
    }

    
    public String getStatus() {
        return this.status;
    }

    
    public Integer getTotalCount() {
        return this.totalCount;
    }

    
    public Integer getCheckedCount() {
        return this.checkedCount;
    }

    
    public Integer getDiffCount() {
        return this.diffCount;
    }

    
    public String getCreator() {
        return this.creator;
    }

    
    public LocalDate getPlanFinish() {
        return this.planFinish;
    }

    
    public LocalDateTime getFinishTime() {
        return this.finishTime;
    }

    
    public Integer getVersion() {
        return this.version;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public void setTaskNo(final String taskNo) {
        this.taskNo = taskNo;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setScope(final String scope) {
        this.scope = scope;
    }

    
    public void setFilter(final Map<String, Object> filter) {
        this.filter = filter;
    }

    
    public void setTargetType(final String targetType) {
        this.targetType = targetType;
    }

    
    public void setInspector(final String inspector) {
        this.inspector = inspector;
    }

    
    public void setStatus(final String status) {
        this.status = status;
    }

    
    public void setTotalCount(final Integer totalCount) {
        this.totalCount = totalCount;
    }

    
    public void setCheckedCount(final Integer checkedCount) {
        this.checkedCount = checkedCount;
    }

    
    public void setDiffCount(final Integer diffCount) {
        this.diffCount = diffCount;
    }

    
    public void setCreator(final String creator) {
        this.creator = creator;
    }

    
    public void setPlanFinish(final LocalDate planFinish) {
        this.planFinish = planFinish;
    }

    
    public void setFinishTime(final LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    
    public void setVersion(final Integer version) {
        this.version = version;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof InventoryTaskEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$totalCount = this.getTotalCount();
            Object other$totalCount = other.getTotalCount();
            if (this$totalCount == null ? other$totalCount == null : this$totalCount.equals(other$totalCount)) {
                Object this$checkedCount = this.getCheckedCount();
                Object other$checkedCount = other.getCheckedCount();
                if (this$checkedCount == null ? other$checkedCount == null : this$checkedCount.equals(other$checkedCount)) {
                    Object this$diffCount = this.getDiffCount();
                    Object other$diffCount = other.getDiffCount();
                    if (this$diffCount == null ? other$diffCount == null : this$diffCount.equals(other$diffCount)) {
                        Object this$version = this.getVersion();
                        Object other$version = other.getVersion();
                        if (this$version == null ? other$version == null : this$version.equals(other$version)) {
                            Object this$taskNo = this.getTaskNo();
                            Object other$taskNo = other.getTaskNo();
                            if (this$taskNo == null ? other$taskNo == null : this$taskNo.equals(other$taskNo)) {
                                Object this$name = this.getName();
                                Object other$name = other.getName();
                                if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                                    Object this$scope = this.getScope();
                                    Object other$scope = other.getScope();
                                    if (this$scope == null ? other$scope == null : this$scope.equals(other$scope)) {
                                        Object this$filter = this.getFilter();
                                        Object other$filter = other.getFilter();
                                        if (this$filter == null ? other$filter == null : this$filter.equals(other$filter)) {
                                            Object this$targetType = this.getTargetType();
                                            Object other$targetType = other.getTargetType();
                                            if (this$targetType == null ? other$targetType == null : this$targetType.equals(other$targetType)) {
                                                Object this$inspector = this.getInspector();
                                                Object other$inspector = other.getInspector();
                                                if (this$inspector == null ? other$inspector == null : this$inspector.equals(other$inspector)) {
                                                    Object this$status = this.getStatus();
                                                    Object other$status = other.getStatus();
                                                    if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                                                        Object this$creator = this.getCreator();
                                                        Object other$creator = other.getCreator();
                                                        if (this$creator == null ? other$creator == null : this$creator.equals(other$creator)) {
                                                            Object this$planFinish = this.getPlanFinish();
                                                            Object other$planFinish = other.getPlanFinish();
                                                            if (this$planFinish == null ? other$planFinish == null : this$planFinish.equals(other$planFinish)) {
                                                                Object this$finishTime = this.getFinishTime();
                                                                Object other$finishTime = other.getFinishTime();
                                                                if (this$finishTime == null
                                                                    ? other$finishTime == null
                                                                    : this$finishTime.equals(other$finishTime)) {
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
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof InventoryTaskEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $totalCount = this.getTotalCount();
        result = result * 59 + ($totalCount == null ? 43 : $totalCount.hashCode());
        Object $checkedCount = this.getCheckedCount();
        result = result * 59 + ($checkedCount == null ? 43 : $checkedCount.hashCode());
        Object $diffCount = this.getDiffCount();
        result = result * 59 + ($diffCount == null ? 43 : $diffCount.hashCode());
        Object $version = this.getVersion();
        result = result * 59 + ($version == null ? 43 : $version.hashCode());
        Object $taskNo = this.getTaskNo();
        result = result * 59 + ($taskNo == null ? 43 : $taskNo.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $scope = this.getScope();
        result = result * 59 + ($scope == null ? 43 : $scope.hashCode());
        Object $filter = this.getFilter();
        result = result * 59 + ($filter == null ? 43 : $filter.hashCode());
        Object $targetType = this.getTargetType();
        result = result * 59 + ($targetType == null ? 43 : $targetType.hashCode());
        Object $inspector = this.getInspector();
        result = result * 59 + ($inspector == null ? 43 : $inspector.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $creator = this.getCreator();
        result = result * 59 + ($creator == null ? 43 : $creator.hashCode());
        Object $planFinish = this.getPlanFinish();
        result = result * 59 + ($planFinish == null ? 43 : $planFinish.hashCode());
        Object $finishTime = this.getFinishTime();
        result = result * 59 + ($finishTime == null ? 43 : $finishTime.hashCode());
        Object $createdAt = this.getCreatedAt();
        return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "InventoryTaskEntity(taskNo="
            + this.getTaskNo()
            + ", name="
            + this.getName()
            + ", scope="
            + this.getScope()
            + ", filter="
            + this.getFilter()
            + ", targetType="
            + this.getTargetType()
            + ", inspector="
            + this.getInspector()
            + ", status="
            + this.getStatus()
            + ", totalCount="
            + this.getTotalCount()
            + ", checkedCount="
            + this.getCheckedCount()
            + ", diffCount="
            + this.getDiffCount()
            + ", creator="
            + this.getCreator()
            + ", planFinish="
            + this.getPlanFinish()
            + ", finishTime="
            + this.getFinishTime()
            + ", version="
            + this.getVersion()
            + ", createdAt="
            + this.getCreatedAt()
            + ")";
    }
}
