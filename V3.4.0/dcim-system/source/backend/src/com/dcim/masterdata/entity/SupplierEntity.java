package com.dcim.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("supplier")
public class SupplierEntity {
    @TableId(
        type = IdType.AUTO
    )
    private Long id;
    private String supplierCode;
    private String name;
    private String type;
    private String contact;
    private String phone;
    private String email;
    private String hotline;
    private String products;
    private String address;
    private String status;
    private String remark;
    private LocalDateTime createdAt;

    
    public Long getId() {
        return this.id;
    }

    
    public String getSupplierCode() {
        return this.supplierCode;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getType() {
        return this.type;
    }

    
    public String getContact() {
        return this.contact;
    }

    
    public String getPhone() {
        return this.phone;
    }

    
    public String getEmail() {
        return this.email;
    }

    
    public String getHotline() {
        return this.hotline;
    }

    
    public String getProducts() {
        return this.products;
    }

    
    public String getAddress() {
        return this.address;
    }

    
    public String getStatus() {
        return this.status;
    }

    
    public String getRemark() {
        return this.remark;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public void setId(final Long id) {
        this.id = id;
    }

    
    public void setSupplierCode(final String supplierCode) {
        this.supplierCode = supplierCode;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setType(final String type) {
        this.type = type;
    }

    
    public void setContact(final String contact) {
        this.contact = contact;
    }

    
    public void setPhone(final String phone) {
        this.phone = phone;
    }

    
    public void setEmail(final String email) {
        this.email = email;
    }

    
    public void setHotline(final String hotline) {
        this.hotline = hotline;
    }

    
    public void setProducts(final String products) {
        this.products = products;
    }

    
    public void setAddress(final String address) {
        this.address = address;
    }

    
    public void setStatus(final String status) {
        this.status = status;
    }

    
    public void setRemark(final String remark) {
        this.remark = remark;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof SupplierEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$id = this.getId();
            Object other$id = other.getId();
            if (this$id == null ? other$id == null : this$id.equals(other$id)) {
                Object this$supplierCode = this.getSupplierCode();
                Object other$supplierCode = other.getSupplierCode();
                if (this$supplierCode == null ? other$supplierCode == null : this$supplierCode.equals(other$supplierCode)) {
                    Object this$name = this.getName();
                    Object other$name = other.getName();
                    if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                        Object this$type = this.getType();
                        Object other$type = other.getType();
                        if (this$type == null ? other$type == null : this$type.equals(other$type)) {
                            Object this$contact = this.getContact();
                            Object other$contact = other.getContact();
                            if (this$contact == null ? other$contact == null : this$contact.equals(other$contact)) {
                                Object this$phone = this.getPhone();
                                Object other$phone = other.getPhone();
                                if (this$phone == null ? other$phone == null : this$phone.equals(other$phone)) {
                                    Object this$email = this.getEmail();
                                    Object other$email = other.getEmail();
                                    if (this$email == null ? other$email == null : this$email.equals(other$email)) {
                                        Object this$hotline = this.getHotline();
                                        Object other$hotline = other.getHotline();
                                        if (this$hotline == null ? other$hotline == null : this$hotline.equals(other$hotline)) {
                                            Object this$products = this.getProducts();
                                            Object other$products = other.getProducts();
                                            if (this$products == null ? other$products == null : this$products.equals(other$products)) {
                                                Object this$address = this.getAddress();
                                                Object other$address = other.getAddress();
                                                if (this$address == null ? other$address == null : this$address.equals(other$address)) {
                                                    Object this$status = this.getStatus();
                                                    Object other$status = other.getStatus();
                                                    if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                                                        Object this$remark = this.getRemark();
                                                        Object other$remark = other.getRemark();
                                                        if (this$remark == null ? other$remark == null : this$remark.equals(other$remark)) {
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
        return other instanceof SupplierEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $supplierCode = this.getSupplierCode();
        result = result * 59 + ($supplierCode == null ? 43 : $supplierCode.hashCode());
        Object $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        Object $type = this.getType();
        result = result * 59 + ($type == null ? 43 : $type.hashCode());
        Object $contact = this.getContact();
        result = result * 59 + ($contact == null ? 43 : $contact.hashCode());
        Object $phone = this.getPhone();
        result = result * 59 + ($phone == null ? 43 : $phone.hashCode());
        Object $email = this.getEmail();
        result = result * 59 + ($email == null ? 43 : $email.hashCode());
        Object $hotline = this.getHotline();
        result = result * 59 + ($hotline == null ? 43 : $hotline.hashCode());
        Object $products = this.getProducts();
        result = result * 59 + ($products == null ? 43 : $products.hashCode());
        Object $address = this.getAddress();
        result = result * 59 + ($address == null ? 43 : $address.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $remark = this.getRemark();
        result = result * 59 + ($remark == null ? 43 : $remark.hashCode());
        Object $createdAt = this.getCreatedAt();
        return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "SupplierEntity(id="
            + this.getId()
            + ", supplierCode="
            + this.getSupplierCode()
            + ", name="
            + this.getName()
            + ", type="
            + this.getType()
            + ", contact="
            + this.getContact()
            + ", phone="
            + this.getPhone()
            + ", email="
            + this.getEmail()
            + ", hotline="
            + this.getHotline()
            + ", products="
            + this.getProducts()
            + ", address="
            + this.getAddress()
            + ", status="
            + this.getStatus()
            + ", remark="
            + this.getRemark()
            + ", createdAt="
            + this.getCreatedAt()
            + ")";
    }
}
