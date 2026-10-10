package com.dcim.workorder.service;


public class WoListQuery {
    private long page = 1L;
    private long size = 20L;
    private String status;
    private String woType;
    private String priority;
    private String keyword;
    private String tab;
    private String handler;
    private String applyFrom;
    private String applyTo;
    private Boolean slaOverdue;

    
    public long getPage() {
        return this.page;
    }

    
    public long getSize() {
        return this.size;
    }

    
    public String getStatus() {
        return this.status;
    }

    
    public String getWoType() {
        return this.woType;
    }

    
    public String getPriority() {
        return this.priority;
    }

    
    public String getKeyword() {
        return this.keyword;
    }

    
    public String getTab() {
        return this.tab;
    }

    
    public String getHandler() {
        return this.handler;
    }

    
    public String getApplyFrom() {
        return this.applyFrom;
    }

    
    public String getApplyTo() {
        return this.applyTo;
    }

    
    public Boolean getSlaOverdue() {
        return this.slaOverdue;
    }

    
    public void setPage(final long page) {
        this.page = page;
    }

    
    public void setSize(final long size) {
        this.size = size;
    }

    
    public void setStatus(final String status) {
        this.status = status;
    }

    
    public void setWoType(final String woType) {
        this.woType = woType;
    }

    
    public void setPriority(final String priority) {
        this.priority = priority;
    }

    
    public void setKeyword(final String keyword) {
        this.keyword = keyword;
    }

    
    public void setTab(final String tab) {
        this.tab = tab;
    }

    
    public void setHandler(final String handler) {
        this.handler = handler;
    }

    
    public void setApplyFrom(final String applyFrom) {
        this.applyFrom = applyFrom;
    }

    
    public void setApplyTo(final String applyTo) {
        this.applyTo = applyTo;
    }

    
    public void setSlaOverdue(final Boolean slaOverdue) {
        this.slaOverdue = slaOverdue;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof WoListQuery other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else if (this.getPage() != other.getPage()) {
            return false;
        } else if (this.getSize() != other.getSize()) {
            return false;
        } else {
            Object this$slaOverdue = this.getSlaOverdue();
            Object other$slaOverdue = other.getSlaOverdue();
            if (this$slaOverdue == null ? other$slaOverdue == null : this$slaOverdue.equals(other$slaOverdue)) {
                Object this$status = this.getStatus();
                Object other$status = other.getStatus();
                if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                    Object this$woType = this.getWoType();
                    Object other$woType = other.getWoType();
                    if (this$woType == null ? other$woType == null : this$woType.equals(other$woType)) {
                        Object this$priority = this.getPriority();
                        Object other$priority = other.getPriority();
                        if (this$priority == null ? other$priority == null : this$priority.equals(other$priority)) {
                            Object this$keyword = this.getKeyword();
                            Object other$keyword = other.getKeyword();
                            if (this$keyword == null ? other$keyword == null : this$keyword.equals(other$keyword)) {
                                Object this$tab = this.getTab();
                                Object other$tab = other.getTab();
                                if (this$tab == null ? other$tab == null : this$tab.equals(other$tab)) {
                                    Object this$handler = this.getHandler();
                                    Object other$handler = other.getHandler();
                                    if (this$handler == null ? other$handler == null : this$handler.equals(other$handler)) {
                                        Object this$applyFrom = this.getApplyFrom();
                                        Object other$applyFrom = other.getApplyFrom();
                                        if (this$applyFrom == null ? other$applyFrom == null : this$applyFrom.equals(other$applyFrom)) {
                                            Object this$applyTo = this.getApplyTo();
                                            Object other$applyTo = other.getApplyTo();
                                            return this$applyTo == null ? other$applyTo == null : this$applyTo.equals(other$applyTo);
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
        return other instanceof WoListQuery;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        long $page = this.getPage();
        result = result * 59 + (int)($page >>> 32 ^ $page);
        long $size = this.getSize();
        result = result * 59 + (int)($size >>> 32 ^ $size);
        Object $slaOverdue = this.getSlaOverdue();
        result = result * 59 + ($slaOverdue == null ? 43 : $slaOverdue.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $woType = this.getWoType();
        result = result * 59 + ($woType == null ? 43 : $woType.hashCode());
        Object $priority = this.getPriority();
        result = result * 59 + ($priority == null ? 43 : $priority.hashCode());
        Object $keyword = this.getKeyword();
        result = result * 59 + ($keyword == null ? 43 : $keyword.hashCode());
        Object $tab = this.getTab();
        result = result * 59 + ($tab == null ? 43 : $tab.hashCode());
        Object $handler = this.getHandler();
        result = result * 59 + ($handler == null ? 43 : $handler.hashCode());
        Object $applyFrom = this.getApplyFrom();
        result = result * 59 + ($applyFrom == null ? 43 : $applyFrom.hashCode());
        Object $applyTo = this.getApplyTo();
        return result * 59 + ($applyTo == null ? 43 : $applyTo.hashCode());
    }

    
    @Override
    public String toString() {
        return "WoListQuery(page="
            + this.getPage()
            + ", size="
            + this.getSize()
            + ", status="
            + this.getStatus()
            + ", woType="
            + this.getWoType()
            + ", priority="
            + this.getPriority()
            + ", keyword="
            + this.getKeyword()
            + ", tab="
            + this.getTab()
            + ", handler="
            + this.getHandler()
            + ", applyFrom="
            + this.getApplyFrom()
            + ", applyTo="
            + this.getApplyTo()
            + ", slaOverdue="
            + this.getSlaOverdue()
            + ")";
    }
}
