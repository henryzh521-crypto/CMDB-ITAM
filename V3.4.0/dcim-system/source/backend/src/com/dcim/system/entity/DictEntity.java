package com.dcim.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("dict")
public class DictEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String dictType;
    private String dictKey;
    private String dictValue;
    private Integer sort;
    private Integer status;

    
    public Long getId() {
        return this.id;
    }

    
    public String getDictType() {
        return this.dictType;
    }

    
    public String getDictKey() {
        return this.dictKey;
    }

    
    public String getDictValue() {
        return this.dictValue;
    }

    
    public Integer getSort() {
        return this.sort;
    }

    
    public Integer getStatus() {
        return this.status;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setDictType(final String dictType) {
        this.dictType = dictType;
    }

    
    public void setDictKey(final String dictKey) {
        this.dictKey = dictKey;
    }

    
    public void setDictValue(final String dictValue) {
        this.dictValue = dictValue;
    }

    
    public void setSort(final Integer sort) {
        this.sort = sort;
    }

    
    public void setStatus(final Integer status) {
        this.status = status;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof DictEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$sort = this.getSort();
                Object other$sort = other.getSort();
                if (this$sort == null ? other$sort == null : this$sort.equals(other$sort)) {
                    Object this$status = this.getStatus();
                    Object other$status = other.getStatus();
                    if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                        Object this$dictType = this.getDictType();
                        Object other$dictType = other.getDictType();
                        if (this$dictType == null ? other$dictType == null : this$dictType.equals(other$dictType)) {
                            Object this$dictKey = this.getDictKey();
                            Object other$dictKey = other.getDictKey();
                            if (this$dictKey == null ? other$dictKey == null : this$dictKey.equals(other$dictKey)) {
                                Object this$dictValue = this.getDictValue();
                                Object other$dictValue = other.getDictValue();
                                return this$dictValue == null ? other$dictValue == null : this$dictValue.equals(other$dictValue);
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
        return other instanceof DictEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $sort = this.getSort();
        result = result * 59 + ($sort == null ? 43 : $sort.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $dictType = this.getDictType();
        result = result * 59 + ($dictType == null ? 43 : $dictType.hashCode());
        Object $dictKey = this.getDictKey();
        result = result * 59 + ($dictKey == null ? 43 : $dictKey.hashCode());
        Object $dictValue = this.getDictValue();
        return result * 59 + ($dictValue == null ? 43 : $dictValue.hashCode());
    }

    
    @Override
    public String toString() {
        return "DictEntity(id="
            + this.getId()
            + ", dictType="
            + this.getDictType()
            + ", dictKey="
            + this.getDictKey()
            + ", dictValue="
            + this.getDictValue()
            + ", sort="
            + this.getSort()
            + ", status="
            + this.getStatus()
            + ")";
    }
}
