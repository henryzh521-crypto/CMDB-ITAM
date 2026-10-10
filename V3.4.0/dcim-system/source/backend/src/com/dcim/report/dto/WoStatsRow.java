package com.dcim.report.dto;


public class WoStatsRow {
    private Long total;
    private Long pending;
    private Long approving;
    private Long processing;
    private Long done;
    private Long rejected;
    private Long closed;
    private Long slaOverdue;

    
    public Long getTotal() {
        return this.total;
    }

    
    public Long getPending() {
        return this.pending;
    }

    
    public Long getApproving() {
        return this.approving;
    }

    
    public Long getProcessing() {
        return this.processing;
    }

    
    public Long getDone() {
        return this.done;
    }

    
    public Long getRejected() {
        return this.rejected;
    }

    
    public Long getClosed() {
        return this.closed;
    }

    
    public Long getSlaOverdue() {
        return this.slaOverdue;
    }

    
    public void setTotal(final Long total) {
        this.total = total;
    }

    
    public void setPending(final Long pending) {
        this.pending = pending;
    }

    
    public void setApproving(final Long approving) {
        this.approving = approving;
    }

    
    public void setProcessing(final Long processing) {
        this.processing = processing;
    }

    
    public void setDone(final Long done) {
        this.done = done;
    }

    
    public void setRejected(final Long rejected) {
        this.rejected = rejected;
    }

    
    public void setClosed(final Long closed) {
        this.closed = closed;
    }

    
    public void setSlaOverdue(final Long slaOverdue) {
        this.slaOverdue = slaOverdue;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof WoStatsRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$total = this.getTotal();
            Object other$total = other.getTotal();
            if (this$total == null ? other$total == null : this$total.equals(other$total)) {
                Object this$pending = this.getPending();
                Object other$pending = other.getPending();
                if (this$pending == null ? other$pending == null : this$pending.equals(other$pending)) {
                    Object this$approving = this.getApproving();
                    Object other$approving = other.getApproving();
                    if (this$approving == null ? other$approving == null : this$approving.equals(other$approving)) {
                        Object this$processing = this.getProcessing();
                        Object other$processing = other.getProcessing();
                        if (this$processing == null ? other$processing == null : this$processing.equals(other$processing)) {
                            Object this$done = this.getDone();
                            Object other$done = other.getDone();
                            if (this$done == null ? other$done == null : this$done.equals(other$done)) {
                                Object this$rejected = this.getRejected();
                                Object other$rejected = other.getRejected();
                                if (this$rejected == null ? other$rejected == null : this$rejected.equals(other$rejected)) {
                                    Object this$closed = this.getClosed();
                                    Object other$closed = other.getClosed();
                                    if (this$closed == null ? other$closed == null : this$closed.equals(other$closed)) {
                                        Object this$slaOverdue = this.getSlaOverdue();
                                        Object other$slaOverdue = other.getSlaOverdue();
                                        return this$slaOverdue == null ? other$slaOverdue == null : this$slaOverdue.equals(other$slaOverdue);
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
        return other instanceof WoStatsRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $total = this.getTotal();
        result = result * 59 + ($total == null ? 43 : $total.hashCode());
        Object $pending = this.getPending();
        result = result * 59 + ($pending == null ? 43 : $pending.hashCode());
        Object $approving = this.getApproving();
        result = result * 59 + ($approving == null ? 43 : $approving.hashCode());
        Object $processing = this.getProcessing();
        result = result * 59 + ($processing == null ? 43 : $processing.hashCode());
        Object $done = this.getDone();
        result = result * 59 + ($done == null ? 43 : $done.hashCode());
        Object $rejected = this.getRejected();
        result = result * 59 + ($rejected == null ? 43 : $rejected.hashCode());
        Object $closed = this.getClosed();
        result = result * 59 + ($closed == null ? 43 : $closed.hashCode());
        Object $slaOverdue = this.getSlaOverdue();
        return result * 59 + ($slaOverdue == null ? 43 : $slaOverdue.hashCode());
    }

    
    @Override
    public String toString() {
        return "WoStatsRow(total="
            + this.getTotal()
            + ", pending="
            + this.getPending()
            + ", approving="
            + this.getApproving()
            + ", processing="
            + this.getProcessing()
            + ", done="
            + this.getDone()
            + ", rejected="
            + this.getRejected()
            + ", closed="
            + this.getClosed()
            + ", slaOverdue="
            + this.getSlaOverdue()
            + ")";
    }
}
