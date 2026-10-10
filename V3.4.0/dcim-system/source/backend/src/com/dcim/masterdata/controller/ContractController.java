package com.dcim.masterdata.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.masterdata.entity.ContractEntity;
import com.dcim.masterdata.mapper.ContractMapper;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
@RequestMapping({"/api/contracts"})
public class ContractController {
    private final ContractMapper contractMapper;
    private final JdbcTemplate jdbc;

    @GetMapping
    @RequirePerm(
        module = "contracts"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String endBefore
    ) {
        Page<ContractEntity> p = new Page(page, size);
        LambdaQueryWrapper<ContractEntity> qw = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            qw.and(
                w -> ((((w.like(ContractEntity::getContractNo, k)).or())
                                .like(ContractEntity::getName, k))
                            .or())
                        .like(ContractEntity::getParty, k)
            );
        }

        if (status != null && !status.isBlank()) {
            qw.eq(ContractEntity::getStatus, status.trim());
        }

        if (type != null && !type.isBlank()) {
            qw.eq(ContractEntity::getType, type.trim());
        }

        if (endBefore != null && !endBefore.isBlank()) {
            LocalDate d = parseDate(endBefore);
            if (d != null) {
                qw.le(ContractEntity::getEndDate, d);
            }
        }

        qw.orderByDesc(ContractEntity::getId);
        this.contractMapper.selectPage(p, qw);
        return ApiResult.ok(PageResult.of(p, this::toRow));
    }

    @GetMapping({"/{contractNo}"})
    @RequirePerm(
        module = "contracts"
    )
    public ApiResult<Map<String, Object>> get(@PathVariable String contractNo) {
        ContractEntity c = this.selectByNo(contractNo);
        if (c == null) {
            throw BusinessException.notFound("合同不存在");
        } else {
            return ApiResult.ok(this.toRow(c));
        }
    }

    @PostMapping
    @RequirePerm(
        module = "contracts",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "contracts",
        objType = "contract",
        objIdSpEL = "#req.contractNo"
    )
    @Transactional
    public ApiResult<Void> create(@RequestBody @Valid ContractController.CreateContractReq req) {
        String no = req.getContractNo().trim();
        if (this.selectByNo(no) != null) {
            throw BusinessException.conflict("合同编号已存在");
        } else {
            ContractEntity c = new ContractEntity();
            c.setContractNo(no);
            c.setName(req.getName().trim());
            c.setType(req.getType().trim());
            c.setParty(blankToNull(req.getParty()));
            c.setAmount(req.getAmount());
            c.setSignDate(parseDate(req.getSignDate()));
            c.setStartDate(parseDate(req.getStartDate()));
            c.setEndDate(parseDate(req.getEndDate()));
            c.setDiscountRate(req.getDiscountRate());
            c.setPurchaseOption(Boolean.TRUE.equals(req.getPurchaseOption()));
            c.setPaymentPlan((List<Object>)(req.getPaymentPlan() == null ? new ArrayList<>() : req.getPaymentPlan()));
            String status = blankToNull(req.getStatus());
            c.setStatus(status == null ? "生效" : status.trim());
            c.setRemark(blankToNull(req.getRemark()));
            c.setCreatedAt(LocalDateTime.now());
            this.validateDates(c);
            this.contractMapper.insert(c);
            return ApiResult.ok();
        }
    }

    @PutMapping({"/{contractNo}"})
    @RequirePerm(
        module = "contracts",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "contracts",
        objType = "contract",
        objIdSpEL = "#contractNo"
    )
    @Transactional
    public ApiResult<Void> update(@PathVariable String contractNo, @RequestBody ContractController.UpdateContractReq req) {
        ContractEntity c = this.selectByNo(contractNo);
        if (c == null) {
            throw BusinessException.notFound("合同不存在");
        } else {
            if (req.getName() != null) {
                if (req.getName().isBlank()) {
                    throw BusinessException.badRequest("合同名称不能为空");
                }

                c.setName(req.getName().trim());
            }

            if (req.getType() != null) {
                if (req.getType().isBlank()) {
                    throw BusinessException.badRequest("合同类型不能为空");
                }

                c.setType(req.getType().trim());
            }

            if (req.getParty() != null) {
                c.setParty(blankToNull(req.getParty()));
            }

            if (req.getAmount() != null) {
                c.setAmount(req.getAmount());
            }

            if (req.getSignDate() != null) {
                c.setSignDate(parseDate(req.getSignDate()));
            }

            if (req.getStartDate() != null) {
                c.setStartDate(parseDate(req.getStartDate()));
            }

            if (req.getEndDate() != null) {
                c.setEndDate(parseDate(req.getEndDate()));
            }

            if (req.getDiscountRate() != null) {
                c.setDiscountRate(req.getDiscountRate());
            }

            if (req.getPurchaseOption() != null) {
                c.setPurchaseOption(req.getPurchaseOption());
            }

            if (req.getPaymentPlan() != null) {
                c.setPaymentPlan(req.getPaymentPlan());
            }

            if (req.getStatus() != null) {
                c.setStatus(blankToNull(req.getStatus()) == null ? "生效" : req.getStatus().trim());
            }

            if (req.getRemark() != null) {
                c.setRemark(blankToNull(req.getRemark()));
            }

            this.validateDates(c);
            this.contractMapper.updateById(c);
            return ApiResult.ok();
        }
    }

    @DeleteMapping({"/{contractNo}"})
    @RequirePerm(
        module = "contracts",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "contracts",
        objType = "contract",
        objIdSpEL = "#contractNo"
    )
    @Transactional
    public ApiResult<Void> delete(@PathVariable String contractNo) {
        ContractEntity c = this.selectByNo(contractNo);
        if (c == null) {
            throw BusinessException.notFound("合同不存在");
        } else {
            Long assets = (Long)this.jdbc
                .queryForObject("SELECT COUNT(*) FROM asset WHERE deleted_at IS NULL AND contract_no = ?", Long.class, new Object[]{c.getContractNo()});
            if (assets != null && assets > 0L) {
                throw BusinessException.conflict("该合同已被资产引用，不允许删除");
            } else {
                this.contractMapper.deleteById(c.getId());
                return ApiResult.ok();
            }
        }
    }

    private ContractEntity selectByNo(String no) {
        return (ContractEntity)this.contractMapper.selectOne(Wrappers.<ContractEntity>lambdaQuery().eq(ContractEntity::getContractNo, no));
    }

    private void validateDates(ContractEntity c) {
        if (c.getStartDate() != null && c.getEndDate() != null && c.getEndDate().isBefore(c.getStartDate())) {
            throw BusinessException.badRequest("合同结束日期不能早于开始日期");
        }
    }

    private static LocalDate parseDate(String s) {
        if (s != null && !s.isBlank()) {
            try {
                return LocalDate.parse(s.trim());
            } catch (Exception var2) {
                throw BusinessException.badRequest("日期格式错误，应为 yyyy-MM-dd：" + s);
            }
        } else {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private Map<String, Object> toRow(ContractEntity c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("contractNo", c.getContractNo());
        row.put("name", c.getName());
        row.put("type", c.getType());
        row.put("party", c.getParty());
        row.put("amount", c.getAmount());
        row.put("signDate", c.getSignDate());
        row.put("startDate", c.getStartDate());
        row.put("endDate", c.getEndDate());
        row.put("discountRate", c.getDiscountRate());
        row.put("purchaseOption", c.getPurchaseOption());
        row.put("paymentPlan", c.getPaymentPlan() == null ? List.of() : c.getPaymentPlan());
        row.put("status", c.getStatus());
        row.put("remark", c.getRemark());
        row.put("createdAt", c.getCreatedAt());
        return row;
    }

    
    public ContractController(final ContractMapper contractMapper, final JdbcTemplate jdbc) {
        this.contractMapper = contractMapper;
        this.jdbc = jdbc;
    }

    public static class CreateContractReq {
        @NotBlank(
            message = "合同编号不能为空"
        )
        @Size(
            max = 50,
            message = "合同编号最长 50 字符"
        )
        private String contractNo;
        @NotBlank(
            message = "合同名称不能为空"
        )
        @Size(
            max = 200,
            message = "合同名称最长 200 字符"
        )
        private String name;
        @NotBlank(
            message = "合同类型不能为空"
        )
        @Size(
            max = 20,
            message = "合同类型最长 20 字符"
        )
        private String type;
        private String party;
        private BigDecimal amount;
        private String signDate;
        private String startDate;
        private String endDate;
        private BigDecimal discountRate;
        private Boolean purchaseOption;
        private List<Object> paymentPlan;
        private String status;
        private String remark;

        
        public String getContractNo() {
            return this.contractNo;
        }

        
        public String getName() {
            return this.name;
        }

        
        public String getType() {
            return this.type;
        }

        
        public String getParty() {
            return this.party;
        }

        
        public BigDecimal getAmount() {
            return this.amount;
        }

        
        public String getSignDate() {
            return this.signDate;
        }

        
        public String getStartDate() {
            return this.startDate;
        }

        
        public String getEndDate() {
            return this.endDate;
        }

        
        public BigDecimal getDiscountRate() {
            return this.discountRate;
        }

        
        public Boolean getPurchaseOption() {
            return this.purchaseOption;
        }

        
        public List<Object> getPaymentPlan() {
            return this.paymentPlan;
        }

        
        public String getStatus() {
            return this.status;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public void setContractNo(final String contractNo) {
            this.contractNo = contractNo;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setType(final String type) {
            this.type = type;
        }

        
        public void setParty(final String party) {
            this.party = party;
        }

        
        public void setAmount(final BigDecimal amount) {
            this.amount = amount;
        }

        
        public void setSignDate(final String signDate) {
            this.signDate = signDate;
        }

        
        public void setStartDate(final String startDate) {
            this.startDate = startDate;
        }

        
        public void setEndDate(final String endDate) {
            this.endDate = endDate;
        }

        
        public void setDiscountRate(final BigDecimal discountRate) {
            this.discountRate = discountRate;
        }

        
        public void setPurchaseOption(final Boolean purchaseOption) {
            this.purchaseOption = purchaseOption;
        }

        
        public void setPaymentPlan(final List<Object> paymentPlan) {
            this.paymentPlan = paymentPlan;
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
            } else if (!(o instanceof ContractController.CreateContractReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$purchaseOption = this.getPurchaseOption();
                Object other$purchaseOption = other.getPurchaseOption();
                if (this$purchaseOption == null ? other$purchaseOption == null : this$purchaseOption.equals(other$purchaseOption)) {
                    Object this$contractNo = this.getContractNo();
                    Object other$contractNo = other.getContractNo();
                    if (this$contractNo == null ? other$contractNo == null : this$contractNo.equals(other$contractNo)) {
                        Object this$name = this.getName();
                        Object other$name = other.getName();
                        if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                            Object this$type = this.getType();
                            Object other$type = other.getType();
                            if (this$type == null ? other$type == null : this$type.equals(other$type)) {
                                Object this$party = this.getParty();
                                Object other$party = other.getParty();
                                if (this$party == null ? other$party == null : this$party.equals(other$party)) {
                                    Object this$amount = this.getAmount();
                                    Object other$amount = other.getAmount();
                                    if (this$amount == null ? other$amount == null : this$amount.equals(other$amount)) {
                                        Object this$signDate = this.getSignDate();
                                        Object other$signDate = other.getSignDate();
                                        if (this$signDate == null ? other$signDate == null : this$signDate.equals(other$signDate)) {
                                            Object this$startDate = this.getStartDate();
                                            Object other$startDate = other.getStartDate();
                                            if (this$startDate == null ? other$startDate == null : this$startDate.equals(other$startDate)) {
                                                Object this$endDate = this.getEndDate();
                                                Object other$endDate = other.getEndDate();
                                                if (this$endDate == null ? other$endDate == null : this$endDate.equals(other$endDate)) {
                                                    Object this$discountRate = this.getDiscountRate();
                                                    Object other$discountRate = other.getDiscountRate();
                                                    if (this$discountRate == null ? other$discountRate == null : this$discountRate.equals(other$discountRate)) {
                                                        Object this$paymentPlan = this.getPaymentPlan();
                                                        Object other$paymentPlan = other.getPaymentPlan();
                                                        if (this$paymentPlan == null ? other$paymentPlan == null : this$paymentPlan.equals(other$paymentPlan)) {
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
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof ContractController.CreateContractReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $purchaseOption = this.getPurchaseOption();
            result = result * 59 + ($purchaseOption == null ? 43 : $purchaseOption.hashCode());
            Object $contractNo = this.getContractNo();
            result = result * 59 + ($contractNo == null ? 43 : $contractNo.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $type = this.getType();
            result = result * 59 + ($type == null ? 43 : $type.hashCode());
            Object $party = this.getParty();
            result = result * 59 + ($party == null ? 43 : $party.hashCode());
            Object $amount = this.getAmount();
            result = result * 59 + ($amount == null ? 43 : $amount.hashCode());
            Object $signDate = this.getSignDate();
            result = result * 59 + ($signDate == null ? 43 : $signDate.hashCode());
            Object $startDate = this.getStartDate();
            result = result * 59 + ($startDate == null ? 43 : $startDate.hashCode());
            Object $endDate = this.getEndDate();
            result = result * 59 + ($endDate == null ? 43 : $endDate.hashCode());
            Object $discountRate = this.getDiscountRate();
            result = result * 59 + ($discountRate == null ? 43 : $discountRate.hashCode());
            Object $paymentPlan = this.getPaymentPlan();
            result = result * 59 + ($paymentPlan == null ? 43 : $paymentPlan.hashCode());
            Object $status = this.getStatus();
            result = result * 59 + ($status == null ? 43 : $status.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "ContractController.CreateContractReq(contractNo="
                + this.getContractNo()
                + ", name="
                + this.getName()
                + ", type="
                + this.getType()
                + ", party="
                + this.getParty()
                + ", amount="
                + this.getAmount()
                + ", signDate="
                + this.getSignDate()
                + ", startDate="
                + this.getStartDate()
                + ", endDate="
                + this.getEndDate()
                + ", discountRate="
                + this.getDiscountRate()
                + ", purchaseOption="
                + this.getPurchaseOption()
                + ", paymentPlan="
                + this.getPaymentPlan()
                + ", status="
                + this.getStatus()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }

    public static class UpdateContractReq {
        private String name;
        private String type;
        private String party;
        private BigDecimal amount;
        private String signDate;
        private String startDate;
        private String endDate;
        private BigDecimal discountRate;
        private Boolean purchaseOption;
        private List<Object> paymentPlan;
        private String status;
        private String remark;

        
        public String getName() {
            return this.name;
        }

        
        public String getType() {
            return this.type;
        }

        
        public String getParty() {
            return this.party;
        }

        
        public BigDecimal getAmount() {
            return this.amount;
        }

        
        public String getSignDate() {
            return this.signDate;
        }

        
        public String getStartDate() {
            return this.startDate;
        }

        
        public String getEndDate() {
            return this.endDate;
        }

        
        public BigDecimal getDiscountRate() {
            return this.discountRate;
        }

        
        public Boolean getPurchaseOption() {
            return this.purchaseOption;
        }

        
        public List<Object> getPaymentPlan() {
            return this.paymentPlan;
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

        
        public void setParty(final String party) {
            this.party = party;
        }

        
        public void setAmount(final BigDecimal amount) {
            this.amount = amount;
        }

        
        public void setSignDate(final String signDate) {
            this.signDate = signDate;
        }

        
        public void setStartDate(final String startDate) {
            this.startDate = startDate;
        }

        
        public void setEndDate(final String endDate) {
            this.endDate = endDate;
        }

        
        public void setDiscountRate(final BigDecimal discountRate) {
            this.discountRate = discountRate;
        }

        
        public void setPurchaseOption(final Boolean purchaseOption) {
            this.purchaseOption = purchaseOption;
        }

        
        public void setPaymentPlan(final List<Object> paymentPlan) {
            this.paymentPlan = paymentPlan;
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
            } else if (!(o instanceof ContractController.UpdateContractReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$purchaseOption = this.getPurchaseOption();
                Object other$purchaseOption = other.getPurchaseOption();
                if (this$purchaseOption == null ? other$purchaseOption == null : this$purchaseOption.equals(other$purchaseOption)) {
                    Object this$name = this.getName();
                    Object other$name = other.getName();
                    if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                        Object this$type = this.getType();
                        Object other$type = other.getType();
                        if (this$type == null ? other$type == null : this$type.equals(other$type)) {
                            Object this$party = this.getParty();
                            Object other$party = other.getParty();
                            if (this$party == null ? other$party == null : this$party.equals(other$party)) {
                                Object this$amount = this.getAmount();
                                Object other$amount = other.getAmount();
                                if (this$amount == null ? other$amount == null : this$amount.equals(other$amount)) {
                                    Object this$signDate = this.getSignDate();
                                    Object other$signDate = other.getSignDate();
                                    if (this$signDate == null ? other$signDate == null : this$signDate.equals(other$signDate)) {
                                        Object this$startDate = this.getStartDate();
                                        Object other$startDate = other.getStartDate();
                                        if (this$startDate == null ? other$startDate == null : this$startDate.equals(other$startDate)) {
                                            Object this$endDate = this.getEndDate();
                                            Object other$endDate = other.getEndDate();
                                            if (this$endDate == null ? other$endDate == null : this$endDate.equals(other$endDate)) {
                                                Object this$discountRate = this.getDiscountRate();
                                                Object other$discountRate = other.getDiscountRate();
                                                if (this$discountRate == null ? other$discountRate == null : this$discountRate.equals(other$discountRate)) {
                                                    Object this$paymentPlan = this.getPaymentPlan();
                                                    Object other$paymentPlan = other.getPaymentPlan();
                                                    if (this$paymentPlan == null ? other$paymentPlan == null : this$paymentPlan.equals(other$paymentPlan)) {
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
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof ContractController.UpdateContractReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $purchaseOption = this.getPurchaseOption();
            result = result * 59 + ($purchaseOption == null ? 43 : $purchaseOption.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $type = this.getType();
            result = result * 59 + ($type == null ? 43 : $type.hashCode());
            Object $party = this.getParty();
            result = result * 59 + ($party == null ? 43 : $party.hashCode());
            Object $amount = this.getAmount();
            result = result * 59 + ($amount == null ? 43 : $amount.hashCode());
            Object $signDate = this.getSignDate();
            result = result * 59 + ($signDate == null ? 43 : $signDate.hashCode());
            Object $startDate = this.getStartDate();
            result = result * 59 + ($startDate == null ? 43 : $startDate.hashCode());
            Object $endDate = this.getEndDate();
            result = result * 59 + ($endDate == null ? 43 : $endDate.hashCode());
            Object $discountRate = this.getDiscountRate();
            result = result * 59 + ($discountRate == null ? 43 : $discountRate.hashCode());
            Object $paymentPlan = this.getPaymentPlan();
            result = result * 59 + ($paymentPlan == null ? 43 : $paymentPlan.hashCode());
            Object $status = this.getStatus();
            result = result * 59 + ($status == null ? 43 : $status.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "ContractController.UpdateContractReq(name="
                + this.getName()
                + ", type="
                + this.getType()
                + ", party="
                + this.getParty()
                + ", amount="
                + this.getAmount()
                + ", signDate="
                + this.getSignDate()
                + ", startDate="
                + this.getStartDate()
                + ", endDate="
                + this.getEndDate()
                + ", discountRate="
                + this.getDiscountRate()
                + ", purchaseOption="
                + this.getPurchaseOption()
                + ", paymentPlan="
                + this.getPaymentPlan()
                + ", status="
                + this.getStatus()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }
}
