package com.dcim.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("category")
public class CategoryEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String categoryCode;
    private String name;
    private String parentCode;
    private Integer level;
    private String path;
    private Integer sort;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getCategoryCode() {
        return this.categoryCode;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getParentCode() {
        return this.parentCode;
    }

    
    public Integer getLevel() {
        return this.level;
    }

    
    public String getPath() {
        return this.path;
    }

    
    public Integer getSort() {
        return this.sort;
    }

    
    public Integer getStatus() {
        return this.status;
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

    
    public void setCategoryCode(final String categoryCode) {
        this.categoryCode = categoryCode;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setParentCode(final String parentCode) {
        this.parentCode = parentCode;
    }

    
    public void setLevel(final Integer level) {
        this.level = level;
    }

    
    public void setPath(final String path) {
        this.path = path;
    }

    
    public void setSort(final Integer sort) {
        this.sort = sort;
    }

    
    public void setStatus(final Integer status) {
        this.status = status;
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
        } else if (!(o instanceof CategoryEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$level = this.getLevel();
                Object other$level = other.getLevel();
                if (this$level == null ? other$level == null : this$level.equals(other$level)) {
                    Object this$sort = this.getSort();
                    Object other$sort = other.getSort();
                    if (this$sort == null ? other$sort == null : this$sort.equals(other$sort)) {
                        Object this$status = this.getStatus();
                        Object other$status = other.getStatus();
                        if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                            Object this$categoryCode = this.getCategoryCode();
                            Object other$categoryCode = other.getCategoryCode();
                            if (this$categoryCode == null ? other$categoryCode == null : this$categoryCode.equals(other$categoryCode)) {
                                Object this$name = this.getName();
                                Object other$name = other.getName();
                                if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                                    Object this$parentCode = this.getParentCode();
                                    Object other$parentCode = other.getParentCode();
                                    if (this$parentCode == null ? other$parentCode == null : this$parentCode.equals(other$parentCode)) {
                                        Object this$path = this.getPath();
                                        Object other$path = other.getPath();
                                        if (this$path == null ? other$path == null : this$path.equals(other$path)) {
                                            Object this$remark = this.getRemark();
                                            Object other$remark = other.getRemark();
                                            if (this$remark == null ? other$remark == null : this$remark.equals(other$remark)) {
                                                Object this$createdAt = this.getCreatedAt();
                                                Object other$createdAt = other.getCreatedAt();
                                                return this$createdAt == null ? other$createdAt == null : this$createdAt.equals(other$createdAt);
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
        return other instanceof CategoryEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $level = this.getLevel();
        result = result * 59 + ($level == null ? 43 : $level.hashCode());
        Object $sort = this.getSort();
        result = result * 59 + ($sort == null ? 43 : $sort.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $categoryCode = this.getCategoryCode();
        result = result * 59 + ($categoryCode == null ? 43 : $categoryCode.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $parentCode = this.getParentCode();
        result = result * 59 + ($parentCode == null ? 43 : $parentCode.hashCode());
        Object $path = this.getPath();
        result = result * 59 + ($path == null ? 43 : $path.hashCode());
        Object $remark = this.getRemark();
        result = result * 59 + ($remark == null ? 43 : $remark.hashCode());
        Object $createdAt = this.getCreatedAt();
        return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "CategoryEntity(id="
            + this.getId()
            + ", categoryCode="
            + this.getCategoryCode()
            + ", name="
            + this.getName()
            + ", parentCode="
            + this.getParentCode()
            + ", level="
            + this.getLevel()
            + ", path="
            + this.getPath()
            + ", sort="
            + this.getSort()
            + ", status="
            + this.getStatus()
            + ", remark="
            + this.getRemark()
            + ", createdAt="
            + this.getCreatedAt()
            + ")";
    }
}
