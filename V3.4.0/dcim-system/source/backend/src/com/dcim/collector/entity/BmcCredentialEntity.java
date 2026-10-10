package com.dcim.collector.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("bmc_credential")
public class BmcCredentialEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String credName;
    private String username;
    private String passwordEnc;
    private LocalDateTime createdAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getCredName() {
        return this.credName;
    }

    
    public String getUsername() {
        return this.username;
    }

    
    public String getPasswordEnc() {
        return this.passwordEnc;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setCredName(final String credName) {
        this.credName = credName;
    }

    
    public void setUsername(final String username) {
        this.username = username;
    }

    
    public void setPasswordEnc(final String passwordEnc) {
        this.passwordEnc = passwordEnc;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof BmcCredentialEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$credName = this.getCredName();
                Object other$credName = other.getCredName();
                if (this$credName == null ? other$credName == null : this$credName.equals(other$credName)) {
                    Object this$username = this.getUsername();
                    Object other$username = other.getUsername();
                    if (this$username == null ? other$username == null : this$username.equals(other$username)) {
                        Object this$passwordEnc = this.getPasswordEnc();
                        Object other$passwordEnc = other.getPasswordEnc();
                        if (this$passwordEnc == null ? other$passwordEnc == null : this$passwordEnc.equals(other$passwordEnc)) {
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
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof BmcCredentialEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $credName = this.getCredName();
        result = result * 59 + ($credName == null ? 43 : $credName.hashCode());
        Object $username = this.getUsername();
        result = result * 59 + ($username == null ? 43 : $username.hashCode());
        Object $passwordEnc = this.getPasswordEnc();
        result = result * 59 + ($passwordEnc == null ? 43 : $passwordEnc.hashCode());
        Object $createdAt = this.getCreatedAt();
        return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "BmcCredentialEntity(id="
            + this.getId()
            + ", credName="
            + this.getCredName()
            + ", username="
            + this.getUsername()
            + ", passwordEnc="
            + this.getPasswordEnc()
            + ", createdAt="
            + this.getCreatedAt()
            + ")";
    }
}
