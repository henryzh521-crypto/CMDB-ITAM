package com.dcim.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("workorder_flow")
public class WorkorderFlowEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String woNo;
    private Integer seq;
    private String action;
    private String actor;
    private String fromStatus;
    private String toStatus;
    private String comment;
    private LocalDateTime createdAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getWoNo() {
        return this.woNo;
    }

    
    public Integer getSeq() {
        return this.seq;
    }

    
    public String getAction() {
        return this.action;
    }

    
    public String getActor() {
        return this.actor;
    }

    
    public String getFromStatus() {
        return this.fromStatus;
    }

    
    public String getToStatus() {
        return this.toStatus;
    }

    
    public String getComment() {
        return this.comment;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setWoNo(final String woNo) {
        this.woNo = woNo;
    }

    
    public void setSeq(final Integer seq) {
        this.seq = seq;
    }

    
    public void setAction(final String action) {
        this.action = action;
    }

    
    public void setActor(final String actor) {
        this.actor = actor;
    }

    
    public void setFromStatus(final String fromStatus) {
        this.fromStatus = fromStatus;
    }

    
    public void setToStatus(final String toStatus) {
        this.toStatus = toStatus;
    }

    
    public void setComment(final String comment) {
        this.comment = comment;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof WorkorderFlowEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$seq = this.getSeq();
                Object other$seq = other.getSeq();
                if (this$seq == null ? other$seq == null : this$seq.equals(other$seq)) {
                    Object this$woNo = this.getWoNo();
                    Object other$woNo = other.getWoNo();
                    if (this$woNo == null ? other$woNo == null : this$woNo.equals(other$woNo)) {
                        Object this$action = this.getAction();
                        Object other$action = other.getAction();
                        if (this$action == null ? other$action == null : this$action.equals(other$action)) {
                            Object this$actor = this.getActor();
                            Object other$actor = other.getActor();
                            if (this$actor == null ? other$actor == null : this$actor.equals(other$actor)) {
                                Object this$fromStatus = this.getFromStatus();
                                Object other$fromStatus = other.getFromStatus();
                                if (this$fromStatus == null ? other$fromStatus == null : this$fromStatus.equals(other$fromStatus)) {
                                    Object this$toStatus = this.getToStatus();
                                    Object other$toStatus = other.getToStatus();
                                    if (this$toStatus == null ? other$toStatus == null : this$toStatus.equals(other$toStatus)) {
                                        Object this$comment = this.getComment();
                                        Object other$comment = other.getComment();
                                        if (this$comment == null ? other$comment == null : this$comment.equals(other$comment)) {
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
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof WorkorderFlowEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $seq = this.getSeq();
        result = result * 59 + ($seq == null ? 43 : $seq.hashCode());
        Object $woNo = this.getWoNo();
        result = result * 59 + ($woNo == null ? 43 : $woNo.hashCode());
        Object $action = this.getAction();
        result = result * 59 + ($action == null ? 43 : $action.hashCode());
        Object $actor = this.getActor();
        result = result * 59 + ($actor == null ? 43 : $actor.hashCode());
        Object $fromStatus = this.getFromStatus();
        result = result * 59 + ($fromStatus == null ? 43 : $fromStatus.hashCode());
        Object $toStatus = this.getToStatus();
        result = result * 59 + ($toStatus == null ? 43 : $toStatus.hashCode());
        Object $comment = this.getComment();
        result = result * 59 + ($comment == null ? 43 : $comment.hashCode());
        Object $createdAt = this.getCreatedAt();
        return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "WorkorderFlowEntity(id="
            + this.getId()
            + ", woNo="
            + this.getWoNo()
            + ", seq="
            + this.getSeq()
            + ", action="
            + this.getAction()
            + ", actor="
            + this.getActor()
            + ", fromStatus="
            + this.getFromStatus()
            + ", toStatus="
            + this.getToStatus()
            + ", comment="
            + this.getComment()
            + ", createdAt="
            + this.getCreatedAt()
            + ")";
    }
}
