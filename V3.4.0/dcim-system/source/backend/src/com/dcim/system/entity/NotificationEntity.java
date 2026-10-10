package com.dcim.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("notification")
public class NotificationEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String username;
    private String type;
    private String title;
    private String content;
    private String refId;
    private Boolean readFlag;
    private LocalDateTime createdAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getUsername() {
        return this.username;
    }

    
    public String getType() {
        return this.type;
    }

    
    public String getTitle() {
        return this.title;
    }

    
    public String getContent() {
        return this.content;
    }

    
    public String getRefId() {
        return this.refId;
    }

    
    public Boolean getReadFlag() {
        return this.readFlag;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setUsername(final String username) {
        this.username = username;
    }

    
    public void setType(final String type) {
        this.type = type;
    }

    
    public void setTitle(final String title) {
        this.title = title;
    }

    
    public void setContent(final String content) {
        this.content = content;
    }

    
    public void setRefId(final String refId) {
        this.refId = refId;
    }

    
    public void setReadFlag(final Boolean readFlag) {
        this.readFlag = readFlag;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof NotificationEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$readFlag = this.getReadFlag();
                Object other$readFlag = other.getReadFlag();
                if (this$readFlag == null ? other$readFlag == null : this$readFlag.equals(other$readFlag)) {
                    Object this$username = this.getUsername();
                    Object other$username = other.getUsername();
                    if (this$username == null ? other$username == null : this$username.equals(other$username)) {
                        Object this$type = this.getType();
                        Object other$type = other.getType();
                        if (this$type == null ? other$type == null : this$type.equals(other$type)) {
                            Object this$title = this.getTitle();
                            Object other$title = other.getTitle();
                            if (this$title == null ? other$title == null : this$title.equals(other$title)) {
                                Object this$content = this.getContent();
                                Object other$content = other.getContent();
                                if (this$content == null ? other$content == null : this$content.equals(other$content)) {
                                    Object this$refId = this.getRefId();
                                    Object other$refId = other.getRefId();
                                    if (this$refId == null ? other$refId == null : this$refId.equals(other$refId)) {
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
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof NotificationEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $readFlag = this.getReadFlag();
        result = result * 59 + ($readFlag == null ? 43 : $readFlag.hashCode());
        Object $username = this.getUsername();
        result = result * 59 + ($username == null ? 43 : $username.hashCode());
        Object $type = this.getType();
        result = result * 59 + ($type == null ? 43 : $type.hashCode());
        Object $title = this.getTitle();
        result = result * 59 + ($title == null ? 43 : $title.hashCode());
        Object $content = this.getContent();
        result = result * 59 + ($content == null ? 43 : $content.hashCode());
        Object $refId = this.getRefId();
        result = result * 59 + ($refId == null ? 43 : $refId.hashCode());
        Object $createdAt = this.getCreatedAt();
        return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "NotificationEntity(id="
            + this.getId()
            + ", username="
            + this.getUsername()
            + ", type="
            + this.getType()
            + ", title="
            + this.getTitle()
            + ", content="
            + this.getContent()
            + ", refId="
            + this.getRefId()
            + ", readFlag="
            + this.getReadFlag()
            + ", createdAt="
            + this.getCreatedAt()
            + ")";
    }
}
