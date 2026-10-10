package com.dcim.masterdata.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.masterdata.entity.SupplierEntity;
import com.dcim.masterdata.mapper.SupplierMapper;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/suppliers"})
public class SupplierController {
    private final SupplierMapper supplierMapper;
    private final JdbcTemplate jdbc;

    @GetMapping
    @RequirePerm(
        module = "suppliers"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String type
    ) {
        Page<SupplierEntity> p = new Page(page, size);
        LambdaQueryWrapper<SupplierEntity> qw = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            qw.and(
                w -> ((((((w.like(
                                                SupplierEntity::getSupplierCode, k
                                            ))
                                            .or())
                                        .like(SupplierEntity::getName, k))
                                    .or())
                                .like(SupplierEntity::getContact, k))
                            .or())
                        .like(SupplierEntity::getPhone, k)
            );
        }

        if (status != null && !status.isBlank()) {
            qw.eq(SupplierEntity::getStatus, status.trim());
        }

        if (type != null && !type.isBlank()) {
            qw.eq(SupplierEntity::getType, type.trim());
        }

        qw.orderByDesc(SupplierEntity::getId);
        this.supplierMapper.selectPage(p, qw);
        return ApiResult.ok(PageResult.of(p, this::toRow));
    }

    @GetMapping({"/{supplierCode}"})
    @RequirePerm(
        module = "suppliers"
    )
    public ApiResult<Map<String, Object>> get(@PathVariable String supplierCode) {
        SupplierEntity s = this.selectByCode(supplierCode);
        if (s == null) {
            throw BusinessException.notFound("供应商不存在");
        } else {
            return ApiResult.ok(this.toRow(s));
        }
    }

    @PostMapping
    @RequirePerm(
        module = "suppliers",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "suppliers",
        objType = "supplier",
        objIdSpEL = "#req.supplierCode"
    )
    @Transactional
    public ApiResult<Void> create(@RequestBody @Valid SupplierController.CreateSupplierReq req) {
        String code = req.getSupplierCode().trim();
        if (this.selectByCode(code) != null) {
            throw BusinessException.conflict("供应商编码已存在");
        } else {
            SupplierEntity s = new SupplierEntity();
            s.setSupplierCode(code);
            s.setName(req.getName().trim());
            s.setType(blankToNull(req.getType()));
            s.setContact(blankToNull(req.getContact()));
            s.setPhone(blankToNull(req.getPhone()));
            s.setEmail(blankToNull(req.getEmail()));
            s.setHotline(blankToNull(req.getHotline()));
            s.setProducts(blankToNull(req.getProducts()));
            s.setAddress(blankToNull(req.getAddress()));
            String status = blankToNull(req.getStatus());
            s.setStatus(status == null ? "ACTIVE" : status.trim());
            s.setRemark(blankToNull(req.getRemark()));
            s.setCreatedAt(LocalDateTime.now());
            this.supplierMapper.insert(s);
            return ApiResult.ok();
        }
    }

    @PutMapping({"/{supplierCode}"})
    @RequirePerm(
        module = "suppliers",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "suppliers",
        objType = "supplier",
        objIdSpEL = "#supplierCode"
    )
    @Transactional
    public ApiResult<Void> update(@PathVariable String supplierCode, @RequestBody SupplierController.UpdateSupplierReq req) {
        SupplierEntity s = this.selectByCode(supplierCode);
        if (s == null) {
            throw BusinessException.notFound("供应商不存在");
        } else {
            if (req.getName() != null) {
                if (req.getName().isBlank()) {
                    throw BusinessException.badRequest("供应商名称不能为空");
                }

                s.setName(req.getName().trim());
            }

            if (req.getType() != null) {
                s.setType(blankToNull(req.getType()));
            }

            if (req.getContact() != null) {
                s.setContact(blankToNull(req.getContact()));
            }

            if (req.getPhone() != null) {
                s.setPhone(blankToNull(req.getPhone()));
            }

            if (req.getEmail() != null) {
                s.setEmail(blankToNull(req.getEmail()));
            }

            if (req.getHotline() != null) {
                s.setHotline(blankToNull(req.getHotline()));
            }

            if (req.getProducts() != null) {
                s.setProducts(blankToNull(req.getProducts()));
            }

            if (req.getAddress() != null) {
                s.setAddress(blankToNull(req.getAddress()));
            }

            if (req.getStatus() != null) {
                s.setStatus(blankToNull(req.getStatus()) == null ? "ACTIVE" : req.getStatus().trim());
            }

            if (req.getRemark() != null) {
                s.setRemark(blankToNull(req.getRemark()));
            }

            this.supplierMapper.updateById(s);
            return ApiResult.ok();
        }
    }

    @DeleteMapping({"/{supplierCode}"})
    @RequirePerm(
        module = "suppliers",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "suppliers",
        objType = "supplier",
        objIdSpEL = "#supplierCode"
    )
    @Transactional
    public ApiResult<Void> delete(@PathVariable String supplierCode) {
        SupplierEntity s = this.selectByCode(supplierCode);
        if (s == null) {
            throw BusinessException.notFound("供应商不存在");
        } else {
            Long materials = (Long)this.jdbc
                .queryForObject("SELECT COUNT(*) FROM material WHERE supplier_code = ?", Long.class, new Object[]{s.getSupplierCode()});
            if (materials != null && materials > 0L) {
                throw BusinessException.conflict("该供应商已被物料引用，不允许删除");
            } else {
                Long batches = (Long)this.jdbc
                    .queryForObject("SELECT COUNT(*) FROM rfid_batch WHERE supplier_code = ?", Long.class, new Object[]{s.getSupplierCode()});
                if (batches != null && batches > 0L) {
                    throw BusinessException.conflict("该供应商已被 RFID 批次引用，不允许删除");
                } else {
                    this.supplierMapper.deleteById(s.getId());
                    return ApiResult.ok();
                }
            }
        }
    }

    private SupplierEntity selectByCode(String code) {
        return (SupplierEntity)this.supplierMapper.selectOne(Wrappers.<SupplierEntity>lambdaQuery().eq(SupplierEntity::getSupplierCode, code));
    }

    private static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private Map<String, Object> toRow(SupplierEntity s) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("supplierCode", s.getSupplierCode());
        row.put("name", s.getName());
        row.put("type", s.getType());
        row.put("contact", s.getContact());
        row.put("phone", s.getPhone());
        row.put("email", s.getEmail());
        row.put("hotline", s.getHotline());
        row.put("products", s.getProducts());
        row.put("address", s.getAddress());
        row.put("status", s.getStatus());
        row.put("remark", s.getRemark());
        row.put("createdAt", s.getCreatedAt());
        return row;
    }

    
    public SupplierController(final SupplierMapper supplierMapper, final JdbcTemplate jdbc) {
        this.supplierMapper = supplierMapper;
        this.jdbc = jdbc;
    }

    public static class CreateSupplierReq {
        @NotBlank(
            message = "供应商编码不能为空"
        )
        @Size(
            max = 50,
            message = "供应商编码最长 50 字符"
        )
        private String supplierCode;
        @NotBlank(
            message = "供应商名称不能为空"
        )
        @Size(
            max = 200,
            message = "供应商名称最长 200 字符"
        )
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

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof SupplierController.CreateSupplierReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
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
                                                        return this$remark == null ? other$remark == null : this$remark.equals(other$remark);
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
            return other instanceof SupplierController.CreateSupplierReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
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
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "SupplierController.CreateSupplierReq(supplierCode="
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
                + ")";
        }
    }

    public static class UpdateSupplierReq {
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

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof SupplierController.UpdateSupplierReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
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
                                                    return this$remark == null ? other$remark == null : this$remark.equals(other$remark);
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
            return other instanceof SupplierController.UpdateSupplierReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
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
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "SupplierController.UpdateSupplierReq(name="
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
                + ")";
        }
    }
}
