package com.dcim.report.dto;


public class CategoryStatusRow {
    private String category;
    private String statusBiz;
    private Long count;

    
    public String getCategory() {
        return this.category;
    }

    
    public String getStatusBiz() {
        return this.statusBiz;
    }

    
    public Long getCount() {
        return this.count;
    }

    
    public void setCategory(final String category) {
        this.category = category;
    }

    
    public void setStatusBiz(final String statusBiz) {
        this.statusBiz = statusBiz;
    }

    
    public void setCount(final Long count) {
        this.count = count;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof CategoryStatusRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$count = this.getCount();
            Object other$count = other.getCount();
            if (this$count == null ? other$count == null : this$count.equals(other$count)) {
                Object this$category = this.getCategory();
                Object other$category = other.getCategory();
                if (this$category == null ? other$category == null : this$category.equals(other$category)) {
                    Object this$statusBiz = this.getStatusBiz();
                    Object other$statusBiz = other.getStatusBiz();
                    return this$statusBiz == null ? other$statusBiz == null : this$statusBiz.equals(other$statusBiz);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof CategoryStatusRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $count = this.getCount();
        result = result * 59 + ($count == null ? 43 : $count.hashCode());
        Object $category = this.getCategory();
        result = result * 59 + ($category == null ? 43 : $category.hashCode());
        Object $statusBiz = this.getStatusBiz();
        return result * 59 + ($statusBiz == null ? 43 : $statusBiz.hashCode());
    }

    
    @Override
    public String toString() {
        return "CategoryStatusRow(category=" + this.getCategory() + ", statusBiz=" + this.getStatusBiz() + ", count=" + this.getCount() + ")";
    }
}
