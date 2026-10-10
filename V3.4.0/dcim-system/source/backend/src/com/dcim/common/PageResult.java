package com.dcim.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import java.util.function.Function;

public class PageResult<T> {
    private long total;
    private List<T> records;
    private long page;
    private long size;
    private long totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    public PageResult(long total, List<T> records) {
        this.total = total;
        this.records = records;
        this.page = 1L;
        this.size = (long)records.size();
        this.totalPages = 1L;
        this.hasNext = false;
        this.hasPrevious = false;
    }

    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> mapper) {
        List<T> records = page.getRecords().stream().map(mapper).toList();
        return new PageResult<>(
            page.getTotal(), records, page.getCurrent(), page.getSize(), page.getPages(), page.getCurrent() < page.getPages(), page.getCurrent() > 1L
        );
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(
            page.getTotal(), page.getRecords(), page.getCurrent(), page.getSize(), page.getPages(), page.getCurrent() < page.getPages(), page.getCurrent() > 1L
        );
    }

    
    public long getTotal() {
        return this.total;
    }

    
    public List<T> getRecords() {
        return this.records;
    }

    
    public long getPage() {
        return this.page;
    }

    
    public long getSize() {
        return this.size;
    }

    
    public long getTotalPages() {
        return this.totalPages;
    }

    
    public boolean isHasNext() {
        return this.hasNext;
    }

    
    public boolean isHasPrevious() {
        return this.hasPrevious;
    }

    
    public void setTotal(final long total) {
        this.total = total;
    }

    
    public void setRecords(final List<T> records) {
        this.records = records;
    }

    
    public void setPage(final long page) {
        this.page = page;
    }

    
    public void setSize(final long size) {
        this.size = size;
    }

    
    public void setTotalPages(final long totalPages) {
        this.totalPages = totalPages;
    }

    
    public void setHasNext(final boolean hasNext) {
        this.hasNext = hasNext;
    }

    
    public void setHasPrevious(final boolean hasPrevious) {
        this.hasPrevious = hasPrevious;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof PageResult<?> other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else if (this.getTotal() != other.getTotal()) {
            return false;
        } else if (this.getPage() != other.getPage()) {
            return false;
        } else if (this.getSize() != other.getSize()) {
            return false;
        } else if (this.getTotalPages() != other.getTotalPages()) {
            return false;
        } else if (this.isHasNext() != other.isHasNext()) {
            return false;
        } else if (this.isHasPrevious() != other.isHasPrevious()) {
            return false;
        } else {
            Object this$records = this.getRecords();
            Object other$records = other.getRecords();
            return this$records == null ? other$records == null : this$records.equals(other$records);
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof PageResult;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        long $total = this.getTotal();
        result = result * 59 + (int)($total >>> 32 ^ $total);
        long $page = this.getPage();
        result = result * 59 + (int)($page >>> 32 ^ $page);
        long $size = this.getSize();
        result = result * 59 + (int)($size >>> 32 ^ $size);
        long $totalPages = this.getTotalPages();
        result = result * 59 + (int)($totalPages >>> 32 ^ $totalPages);
        result = result * 59 + (this.isHasNext() ? 79 : 97);
        result = result * 59 + (this.isHasPrevious() ? 79 : 97);
        Object $records = this.getRecords();
        return result * 59 + ($records == null ? 43 : $records.hashCode());
    }

    
    @Override
    public String toString() {
        return "PageResult(total="
            + this.getTotal()
            + ", records="
            + this.getRecords()
            + ", page="
            + this.getPage()
            + ", size="
            + this.getSize()
            + ", totalPages="
            + this.getTotalPages()
            + ", hasNext="
            + this.isHasNext()
            + ", hasPrevious="
            + this.isHasPrevious()
            + ")";
    }

    
    public PageResult(
        final long total, final List<T> records, final long page, final long size, final long totalPages, final boolean hasNext, final boolean hasPrevious
    ) {
        this.total = total;
        this.records = records;
        this.page = page;
        this.size = size;
        this.totalPages = totalPages;
        this.hasNext = hasNext;
        this.hasPrevious = hasPrevious;
    }
}
