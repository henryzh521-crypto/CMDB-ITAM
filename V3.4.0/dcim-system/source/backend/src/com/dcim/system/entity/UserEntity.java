package com.dcim.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("users")
public class UserEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String username;
    private String passwordHash;
    private String name;
    private String department;
    private String phone;
    private String email;
    private String wecomUserid;
    private Integer status;
    private Boolean mustChangePwd;
    private String authSource;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getUsername() {
        return this.username;
    }

    
    public String getPasswordHash() {
        return this.passwordHash;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getDepartment() {
        return this.department;
    }

    
    public String getPhone() {
        return this.phone;
    }

    
    public String getEmail() {
        return this.email;
    }

    
    public String getWecomUserid() {
        return this.wecomUserid;
    }

    
    public Integer getStatus() {
        return this.status;
    }

    
    public Boolean getMustChangePwd() {
        return this.mustChangePwd;
    }

    
    public String getAuthSource() {
        return this.authSource;
    }

    
    public LocalDateTime getLastLoginAt() {
        return this.lastLoginAt;
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

    
    public void setUsername(final String username) {
        this.username = username;
    }

    
    public void setPasswordHash(final String passwordHash) {
        this.passwordHash = passwordHash;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setDepartment(final String department) {
        this.department = department;
    }

    
    public void setPhone(final String phone) {
        this.phone = phone;
    }

    
    public void setEmail(final String email) {
        this.email = email;
    }

    
    public void setWecomUserid(final String wecomUserid) {
        this.wecomUserid = wecomUserid;
    }

    
    public void setStatus(final Integer status) {
        this.status = status;
    }

    
    public void setMustChangePwd(final Boolean mustChangePwd) {
        this.mustChangePwd = mustChangePwd;
    }

    
    public void setAuthSource(final String authSource) {
        this.authSource = authSource;
    }

    
    public void setLastLoginAt(final LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
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
        } else if (!(o instanceof UserEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$status = this.getStatus();
                Object other$status = other.getStatus();
                if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                    Object this$mustChangePwd = this.getMustChangePwd();
                    Object other$mustChangePwd = other.getMustChangePwd();
                    if (this$mustChangePwd == null ? other$mustChangePwd == null : this$mustChangePwd.equals(other$mustChangePwd)) {
                        Object this$username = this.getUsername();
                        Object other$username = other.getUsername();
                        if (this$username == null ? other$username == null : this$username.equals(other$username)) {
                            Object this$passwordHash = this.getPasswordHash();
                            Object other$passwordHash = other.getPasswordHash();
                            if (this$passwordHash == null ? other$passwordHash == null : this$passwordHash.equals(other$passwordHash)) {
                                Object this$name = this.getName();
                                Object other$name = other.getName();
                                if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                                    Object this$department = this.getDepartment();
                                    Object other$department = other.getDepartment();
                                    if (this$department == null ? other$department == null : this$department.equals(other$department)) {
                                        Object this$phone = this.getPhone();
                                        Object other$phone = other.getPhone();
                                        if (this$phone == null ? other$phone == null : this$phone.equals(other$phone)) {
                                            Object this$email = this.getEmail();
                                            Object other$email = other.getEmail();
                                            if (this$email == null ? other$email == null : this$email.equals(other$email)) {
                                                Object this$wecomUserid = this.getWecomUserid();
                                                Object other$wecomUserid = other.getWecomUserid();
                                                if (this$wecomUserid == null ? other$wecomUserid == null : this$wecomUserid.equals(other$wecomUserid)) {
                                                    Object this$authSource = this.getAuthSource();
                                                    Object other$authSource = other.getAuthSource();
                                                    if (this$authSource == null ? other$authSource == null : this$authSource.equals(other$authSource)) {
                                                        Object this$lastLoginAt = this.getLastLoginAt();
                                                        Object other$lastLoginAt = other.getLastLoginAt();
                                                        if (this$lastLoginAt == null ? other$lastLoginAt == null : this$lastLoginAt.equals(other$lastLoginAt)) {
                                                            Object this$createdAt = this.getCreatedAt();
                                                            Object other$createdAt = other.getCreatedAt();
                                                            if (this$createdAt == null ? other$createdAt == null : this$createdAt.equals(other$createdAt)) {
                                                                Object this$updatedAt = this.getUpdatedAt();
                                                                Object other$updatedAt = other.getUpdatedAt();
                                                                if (this$updatedAt == null ? other$updatedAt == null : this$updatedAt.equals(other$updatedAt)) {
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
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof UserEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $mustChangePwd = this.getMustChangePwd();
        result = result * 59 + ($mustChangePwd == null ? 43 : $mustChangePwd.hashCode());
        Object $username = this.getUsername();
        result = result * 59 + ($username == null ? 43 : $username.hashCode());
        Object $passwordHash = this.getPasswordHash();
        result = result * 59 + ($passwordHash == null ? 43 : $passwordHash.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $department = this.getDepartment();
        result = result * 59 + ($department == null ? 43 : $department.hashCode());
        Object $phone = this.getPhone();
        result = result * 59 + ($phone == null ? 43 : $phone.hashCode());
        Object $email = this.getEmail();
        result = result * 59 + ($email == null ? 43 : $email.hashCode());
        Object $wecomUserid = this.getWecomUserid();
        result = result * 59 + ($wecomUserid == null ? 43 : $wecomUserid.hashCode());
        Object $authSource = this.getAuthSource();
        result = result * 59 + ($authSource == null ? 43 : $authSource.hashCode());
        Object $lastLoginAt = this.getLastLoginAt();
        result = result * 59 + ($lastLoginAt == null ? 43 : $lastLoginAt.hashCode());
        Object $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
        Object $updatedAt = this.getUpdatedAt();
        result = result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
        Object $deletedAt = this.getDeletedAt();
        return result * 59 + ($deletedAt == null ? 43 : $deletedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "UserEntity(id="
            + this.getId()
            + ", username="
            + this.getUsername()
            + ", passwordHash="
            + this.getPasswordHash()
            + ", name="
            + this.getName()
            + ", department="
            + this.getDepartment()
            + ", phone="
            + this.getPhone()
            + ", email="
            + this.getEmail()
            + ", wecomUserid="
            + this.getWecomUserid()
            + ", status="
            + this.getStatus()
            + ", mustChangePwd="
            + this.getMustChangePwd()
            + ", authSource="
            + this.getAuthSource()
            + ", lastLoginAt="
            + this.getLastLoginAt()
            + ", createdAt="
            + this.getCreatedAt()
            + ", updatedAt="
            + this.getUpdatedAt()
            + ", deletedAt="
            + this.getDeletedAt()
            + ")";
    }
}
