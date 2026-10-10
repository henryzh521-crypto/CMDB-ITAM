package com.dcim.change.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.StringArrayTypeHandler;
import java.time.LocalDateTime;
import java.util.List;

@TableName(
    value = "change_request",
    autoResultMap = true
)
public class ChangeRequest {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String crNo;
    private String title;
    private String description;
    private String changeType;
    private String riskLevel;
    private String impactScope;
    private String rollbackPlan;
    private String status;
    private String requester;
    private Boolean cabApproval;
    @TableField(
        typeHandler = StringArrayTypeHandler.class
    )
    private List<String> cabApprovers;
    private String cabComment;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private LocalDateTime actualStart;
    private LocalDateTime actualEnd;
    private String closeNote;
    private String cancelReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getCrNo() {
        return this.crNo;
    }

    
    public String getTitle() {
        return this.title;
    }

    
    public String getDescription() {
        return this.description;
    }

    
    public String getChangeType() {
        return this.changeType;
    }

    
    public String getRiskLevel() {
        return this.riskLevel;
    }

    
    public String getImpactScope() {
        return this.impactScope;
    }

    
    public String getRollbackPlan() {
        return this.rollbackPlan;
    }

    
    public String getStatus() {
        return this.status;
    }

    
    public String getRequester() {
        return this.requester;
    }

    
    public Boolean getCabApproval() {
        return this.cabApproval;
    }

    
    public List<String> getCabApprovers() {
        return this.cabApprovers;
    }

    
    public String getCabComment() {
        return this.cabComment;
    }

    
    public String getApprovedBy() {
        return this.approvedBy;
    }

    
    public LocalDateTime getApprovedAt() {
        return this.approvedAt;
    }

    
    public LocalDateTime getScheduledStart() {
        return this.scheduledStart;
    }

    
    public LocalDateTime getScheduledEnd() {
        return this.scheduledEnd;
    }

    
    public LocalDateTime getActualStart() {
        return this.actualStart;
    }

    
    public LocalDateTime getActualEnd() {
        return this.actualEnd;
    }

    
    public String getCloseNote() {
        return this.closeNote;
    }

    
    public String getCancelReason() {
        return this.cancelReason;
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

    
    public void setCrNo(final String crNo) {
        this.crNo = crNo;
    }

    
    public void setTitle(final String title) {
        this.title = title;
    }

    
    public void setDescription(final String description) {
        this.description = description;
    }

    
    public void setChangeType(final String changeType) {
        this.changeType = changeType;
    }

    
    public void setRiskLevel(final String riskLevel) {
        this.riskLevel = riskLevel;
    }

    
    public void setImpactScope(final String impactScope) {
        this.impactScope = impactScope;
    }

    
    public void setRollbackPlan(final String rollbackPlan) {
        this.rollbackPlan = rollbackPlan;
    }

    
    public void setStatus(final String status) {
        this.status = status;
    }

    
    public void setRequester(final String requester) {
        this.requester = requester;
    }

    
    public void setCabApproval(final Boolean cabApproval) {
        this.cabApproval = cabApproval;
    }

    
    public void setCabApprovers(final List<String> cabApprovers) {
        this.cabApprovers = cabApprovers;
    }

    
    public void setCabComment(final String cabComment) {
        this.cabComment = cabComment;
    }

    
    public void setApprovedBy(final String approvedBy) {
        this.approvedBy = approvedBy;
    }

    
    public void setApprovedAt(final LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    
    public void setScheduledStart(final LocalDateTime scheduledStart) {
        this.scheduledStart = scheduledStart;
    }

    
    public void setScheduledEnd(final LocalDateTime scheduledEnd) {
        this.scheduledEnd = scheduledEnd;
    }

    
    public void setActualStart(final LocalDateTime actualStart) {
        this.actualStart = actualStart;
    }

    
    public void setActualEnd(final LocalDateTime actualEnd) {
        this.actualEnd = actualEnd;
    }

    
    public void setCloseNote(final String closeNote) {
        this.closeNote = closeNote;
    }

    
    public void setCancelReason(final String cancelReason) {
        this.cancelReason = cancelReason;
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
        } else if (!(o instanceof ChangeRequest other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$cabApproval = this.getCabApproval();
                Object other$cabApproval = other.getCabApproval();
                if (this$cabApproval == null ? other$cabApproval == null : this$cabApproval.equals(other$cabApproval)) {
                    Object this$crNo = this.getCrNo();
                    Object other$crNo = other.getCrNo();
                    if (this$crNo == null ? other$crNo == null : this$crNo.equals(other$crNo)) {
                        Object this$title = this.getTitle();
                        Object other$title = other.getTitle();
                        if (this$title == null ? other$title == null : this$title.equals(other$title)) {
                            Object this$description = this.getDescription();
                            Object other$description = other.getDescription();
                            if (this$description == null ? other$description == null : this$description.equals(other$description)) {
                                Object this$changeType = this.getChangeType();
                                Object other$changeType = other.getChangeType();
                                if (this$changeType == null ? other$changeType == null : this$changeType.equals(other$changeType)) {
                                    Object this$riskLevel = this.getRiskLevel();
                                    Object other$riskLevel = other.getRiskLevel();
                                    if (this$riskLevel == null ? other$riskLevel == null : this$riskLevel.equals(other$riskLevel)) {
                                        Object this$impactScope = this.getImpactScope();
                                        Object other$impactScope = other.getImpactScope();
                                        if (this$impactScope == null ? other$impactScope == null : this$impactScope.equals(other$impactScope)) {
                                            Object this$rollbackPlan = this.getRollbackPlan();
                                            Object other$rollbackPlan = other.getRollbackPlan();
                                            if (this$rollbackPlan == null ? other$rollbackPlan == null : this$rollbackPlan.equals(other$rollbackPlan)) {
                                                Object this$status = this.getStatus();
                                                Object other$status = other.getStatus();
                                                if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                                                    Object this$requester = this.getRequester();
                                                    Object other$requester = other.getRequester();
                                                    if (this$requester == null ? other$requester == null : this$requester.equals(other$requester)) {
                                                        Object this$cabApprovers = this.getCabApprovers();
                                                        Object other$cabApprovers = other.getCabApprovers();
                                                        if (this$cabApprovers == null
                                                            ? other$cabApprovers == null
                                                            : this$cabApprovers.equals(other$cabApprovers)) {
                                                            Object this$cabComment = this.getCabComment();
                                                            Object other$cabComment = other.getCabComment();
                                                            if (this$cabComment == null ? other$cabComment == null : this$cabComment.equals(other$cabComment)) {
                                                                Object this$approvedBy = this.getApprovedBy();
                                                                Object other$approvedBy = other.getApprovedBy();
                                                                if (this$approvedBy == null
                                                                    ? other$approvedBy == null
                                                                    : this$approvedBy.equals(other$approvedBy)) {
                                                                    Object this$approvedAt = this.getApprovedAt();
                                                                    Object other$approvedAt = other.getApprovedAt();
                                                                    if (this$approvedAt == null
                                                                        ? other$approvedAt == null
                                                                        : this$approvedAt.equals(other$approvedAt)) {
                                                                        Object this$scheduledStart = this.getScheduledStart();
                                                                        Object other$scheduledStart = other.getScheduledStart();
                                                                        if (this$scheduledStart == null
                                                                            ? other$scheduledStart == null
                                                                            : this$scheduledStart.equals(other$scheduledStart)) {
                                                                            Object this$scheduledEnd = this.getScheduledEnd();
                                                                            Object other$scheduledEnd = other.getScheduledEnd();
                                                                            if (this$scheduledEnd == null
                                                                                ? other$scheduledEnd == null
                                                                                : this$scheduledEnd.equals(other$scheduledEnd)) {
                                                                                Object this$actualStart = this.getActualStart();
                                                                                Object other$actualStart = other.getActualStart();
                                                                                if (this$actualStart == null
                                                                                    ? other$actualStart == null
                                                                                    : this$actualStart.equals(other$actualStart)) {
                                                                                    Object this$actualEnd = this.getActualEnd();
                                                                                    Object other$actualEnd = other.getActualEnd();
                                                                                    if (this$actualEnd == null
                                                                                        ? other$actualEnd == null
                                                                                        : this$actualEnd.equals(other$actualEnd)) {
                                                                                        Object this$closeNote = this.getCloseNote();
                                                                                        Object other$closeNote = other.getCloseNote();
                                                                                        if (this$closeNote == null
                                                                                            ? other$closeNote == null
                                                                                            : this$closeNote.equals(other$closeNote)) {
                                                                                            Object this$cancelReason = this.getCancelReason();
                                                                                            Object other$cancelReason = other.getCancelReason();
                                                                                            if (this$cancelReason == null
                                                                                                ? other$cancelReason == null
                                                                                                : this$cancelReason.equals(other$cancelReason)) {
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
        return other instanceof ChangeRequest;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $cabApproval = this.getCabApproval();
        result = result * 59 + ($cabApproval == null ? 43 : $cabApproval.hashCode());
        Object $crNo = this.getCrNo();
        result = result * 59 + ($crNo == null ? 43 : $crNo.hashCode());
        Object $title = this.getTitle();
        result = result * 59 + ($title == null ? 43 : $title.hashCode());
        Object $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        Object $changeType = this.getChangeType();
        result = result * 59 + ($changeType == null ? 43 : $changeType.hashCode());
        Object $riskLevel = this.getRiskLevel();
        result = result * 59 + ($riskLevel == null ? 43 : $riskLevel.hashCode());
        Object $impactScope = this.getImpactScope();
        result = result * 59 + ($impactScope == null ? 43 : $impactScope.hashCode());
        Object $rollbackPlan = this.getRollbackPlan();
        result = result * 59 + ($rollbackPlan == null ? 43 : $rollbackPlan.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $requester = this.getRequester();
        result = result * 59 + ($requester == null ? 43 : $requester.hashCode());
        Object $cabApprovers = this.getCabApprovers();
        result = result * 59 + ($cabApprovers == null ? 43 : $cabApprovers.hashCode());
        Object $cabComment = this.getCabComment();
        result = result * 59 + ($cabComment == null ? 43 : $cabComment.hashCode());
        Object $approvedBy = this.getApprovedBy();
        result = result * 59 + ($approvedBy == null ? 43 : $approvedBy.hashCode());
        Object $approvedAt = this.getApprovedAt();
        result = result * 59 + ($approvedAt == null ? 43 : $approvedAt.hashCode());
        Object $scheduledStart = this.getScheduledStart();
        result = result * 59 + ($scheduledStart == null ? 43 : $scheduledStart.hashCode());
        Object $scheduledEnd = this.getScheduledEnd();
        result = result * 59 + ($scheduledEnd == null ? 43 : $scheduledEnd.hashCode());
        Object $actualStart = this.getActualStart();
        result = result * 59 + ($actualStart == null ? 43 : $actualStart.hashCode());
        Object $actualEnd = this.getActualEnd();
        result = result * 59 + ($actualEnd == null ? 43 : $actualEnd.hashCode());
        Object $closeNote = this.getCloseNote();
        result = result * 59 + ($closeNote == null ? 43 : $closeNote.hashCode());
        Object $cancelReason = this.getCancelReason();
        result = result * 59 + ($cancelReason == null ? 43 : $cancelReason.hashCode());
        Object $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
        Object $updatedAt = this.getUpdatedAt();
        result = result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
        Object $deletedAt = this.getDeletedAt();
        return result * 59 + ($deletedAt == null ? 43 : $deletedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "ChangeRequest(id="
            + this.getId()
            + ", crNo="
            + this.getCrNo()
            + ", title="
            + this.getTitle()
            + ", description="
            + this.getDescription()
            + ", changeType="
            + this.getChangeType()
            + ", riskLevel="
            + this.getRiskLevel()
            + ", impactScope="
            + this.getImpactScope()
            + ", rollbackPlan="
            + this.getRollbackPlan()
            + ", status="
            + this.getStatus()
            + ", requester="
            + this.getRequester()
            + ", cabApproval="
            + this.getCabApproval()
            + ", cabApprovers="
            + this.getCabApprovers()
            + ", cabComment="
            + this.getCabComment()
            + ", approvedBy="
            + this.getApprovedBy()
            + ", approvedAt="
            + this.getApprovedAt()
            + ", scheduledStart="
            + this.getScheduledStart()
            + ", scheduledEnd="
            + this.getScheduledEnd()
            + ", actualStart="
            + this.getActualStart()
            + ", actualEnd="
            + this.getActualEnd()
            + ", closeNote="
            + this.getCloseNote()
            + ", cancelReason="
            + this.getCancelReason()
            + ", createdAt="
            + this.getCreatedAt()
            + ", updatedAt="
            + this.getUpdatedAt()
            + ", deletedAt="
            + this.getDeletedAt()
            + ")";
    }
}
