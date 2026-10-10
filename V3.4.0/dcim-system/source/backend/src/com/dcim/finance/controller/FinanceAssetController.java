package com.dcim.finance.controller;

import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.finance.dto.FinanceAssetVO;
import com.dcim.finance.service.FinanceAssetService;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/finance/assets"})
public class FinanceAssetController {
    private static final int BATCH_LIMIT = 500;
    private final FinanceAssetService financeAssetService;

    @GetMapping
    @RequirePerm(
        module = "finance"
    )
    public ApiResult<PageResult<FinanceAssetVO>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String statusFin,
        @RequestParam(required = false) Boolean leaseFlag
    ) {
        return ApiResult.ok(this.financeAssetService.list(page, size, keyword, statusFin, leaseFlag));
    }

    @PostMapping
    @RequirePerm(
        module = "finance",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "finance",
        objType = "asset_finance",
        objIdSpEL = "#req.assetNo"
    )
    public ApiResult<FinanceAssetVO> register(@RequestBody FinanceAssetController.RegisterReq req) {
        return ApiResult.ok(
            this.financeAssetService
                .register(
                    req.getAssetNo(),
                    req.getOriginValue(),
                    req.getResidualRate(),
                    req.getLifeMonths(),
                    req.getDepMethod(),
                    req.getDepStartDate(),
                    req.getLeaseFlag()
                )
        );
    }

    @PostMapping({"/{assetNo}/capitalize"})
    @RequirePerm(
        module = "finance",
        write = true
    )
    @Audit(
        action = "CAPITALIZE",
        module = "finance",
        objType = "asset_finance",
        objIdSpEL = "#assetNo"
    )
    public ApiResult<FinanceAssetVO> capitalize(@PathVariable String assetNo) {
        return ApiResult.ok(this.financeAssetService.capitalize(assetNo));
    }

    @PostMapping({"/{assetNo}/impairment"})
    @RequirePerm(
        module = "finance",
        write = true
    )
    @Audit(
        action = "IMPAIR",
        module = "finance",
        objType = "asset_finance",
        objIdSpEL = "#assetNo"
    )
    public ApiResult<FinanceAssetVO> impairment(@PathVariable String assetNo, @RequestBody FinanceAssetController.ImpairmentReq req) {
        return ApiResult.ok(this.financeAssetService.impair(assetNo, req.getAmount()));
    }

    @PostMapping({"/{assetNo}/dispose"})
    @RequirePerm(
        module = "finance",
        write = true
    )
    @Audit(
        action = "DISPOSE",
        module = "finance",
        objType = "asset_finance",
        objIdSpEL = "#assetNo"
    )
    public ApiResult<FinanceAssetVO> dispose(@PathVariable String assetNo) {
        return ApiResult.ok(this.financeAssetService.dispose(assetNo));
    }

    @PostMapping({"/{assetNo}/write-off"})
    @RequirePerm(
        module = "finance",
        write = true
    )
    @Audit(
        action = "WRITE_OFF",
        module = "finance",
        objType = "asset_finance",
        objIdSpEL = "#assetNo"
    )
    public ApiResult<FinanceAssetVO> writeOff(@PathVariable String assetNo) {
        return ApiResult.ok(this.financeAssetService.writeOff(assetNo));
    }

    @PostMapping({"/batch/register"})
    @RequirePerm(
        module = "finance",
        write = true,
        action = "create"
    )
    @Audit(
        action = "BATCH_CREATE",
        module = "finance",
        objType = "asset_finance"
    )
    public ApiResult<FinanceAssetController.BatchResult> batchRegister(@RequestBody FinanceAssetController.BatchRegisterReq req) {
        if (req.getOriginValue() == null || req.getOriginValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.badRequest("原值必须大于 0");
        } else if (req.getLifeMonths() == null || req.getLifeMonths() <= 0) {
            throw BusinessException.badRequest("折旧年限（月）必须大于 0");
        } else if (req.getDepStartDate() != null && !req.getDepStartDate().isBlank()) {
            List<String> nos = this.normalizeBatchNos(req.getAssetNos());
            FinanceAssetController.BatchResult r = new FinanceAssetController.BatchResult();
            r.setTotal(nos.size());

            for (String no : nos) {
                try {
                    r.getChanged()
                        .add(
                            FinanceAssetController.BatchItem.done(
                                this.financeAssetService
                                    .register(
                                        no,
                                        req.getOriginValue(),
                                        req.getResidualRate(),
                                        req.getLifeMonths(),
                                        req.getDepMethod(),
                                        req.getDepStartDate(),
                                        req.getLeaseFlag()
                                    )
                            )
                        );
                } catch (BusinessException var7) {
                    r.getSkipped().add(FinanceAssetController.BatchItem.skipped(no, var7.getMessage()));
                }
            }

            return ApiResult.ok(r);
        } else {
            throw BusinessException.badRequest("折旧起始日期不能为空（yyyy-MM-dd）");
        }
    }

    @PostMapping({"/batch/capitalize"})
    @RequirePerm(
        module = "finance",
        write = true
    )
    @Audit(
        action = "BATCH_CAPITALIZE",
        module = "finance",
        objType = "asset_finance"
    )
    public ApiResult<FinanceAssetController.BatchResult> batchCapitalize(@RequestBody FinanceAssetController.BatchNosReq req) {
        return this.runBatchAction(req, this.financeAssetService::capitalize);
    }

    @PostMapping({"/batch/impairment"})
    @RequirePerm(
        module = "finance",
        write = true
    )
    @Audit(
        action = "BATCH_IMPAIR",
        module = "finance",
        objType = "asset_finance"
    )
    public ApiResult<FinanceAssetController.BatchResult> batchImpair(@RequestBody FinanceAssetController.BatchImpairReq req) {
        if (req.getAmount() != null && req.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal amount = req.getAmount();
            return this.runBatchAction(req, no -> this.financeAssetService.impair(no, amount));
        } else {
            throw BusinessException.badRequest("减值金额必须大于 0");
        }
    }

    @PostMapping({"/batch/dispose"})
    @RequirePerm(
        module = "finance",
        write = true
    )
    @Audit(
        action = "BATCH_DISPOSE",
        module = "finance",
        objType = "asset_finance"
    )
    public ApiResult<FinanceAssetController.BatchResult> batchDispose(@RequestBody FinanceAssetController.BatchNosReq req) {
        return this.runBatchAction(req, this.financeAssetService::dispose);
    }

    @PostMapping({"/batch/write-off"})
    @RequirePerm(
        module = "finance",
        write = true
    )
    @Audit(
        action = "BATCH_WRITE_OFF",
        module = "finance",
        objType = "asset_finance"
    )
    public ApiResult<FinanceAssetController.BatchResult> batchWriteOff(@RequestBody FinanceAssetController.BatchNosReq req) {
        return this.runBatchAction(req, this.financeAssetService::writeOff);
    }

    private ApiResult<FinanceAssetController.BatchResult> runBatchAction(FinanceAssetController.BatchNosReq req, Function<String, FinanceAssetVO> action) {
        List<String> nos = this.normalizeBatchNos(req.getAssetNos());
        FinanceAssetController.BatchResult r = new FinanceAssetController.BatchResult();
        r.setTotal(nos.size());

        for (String no : nos) {
            try {
                r.getChanged().add(FinanceAssetController.BatchItem.done(action.apply(no)));
            } catch (BusinessException var8) {
                r.getSkipped().add(FinanceAssetController.BatchItem.skipped(no, var8.getMessage()));
            }
        }

        return ApiResult.ok(r);
    }

    private List<String> normalizeBatchNos(List<String> assetNos) {
        List<String> nos = new ArrayList<>();

        for (String n : assetNos) {
            if (n != null && !n.isBlank()) {
                nos.add(n.trim());
            }
        }

        nos = new ArrayList<>(new LinkedHashSet<>(nos));
        if (nos.isEmpty()) {
            throw BusinessException.badRequest("资产编号列表不能为空");
        } else if (nos.size() > 500) {
            throw BusinessException.badRequest("单次批量操作最多 500 台，当前 " + nos.size() + " 台");
        } else {
            return nos;
        }
    }

    
    public FinanceAssetController(final FinanceAssetService financeAssetService) {
        this.financeAssetService = financeAssetService;
    }

    public static class BatchImpairReq extends FinanceAssetController.BatchNosReq {
        private BigDecimal amount;

        
        public BigDecimal getAmount() {
            return this.amount;
        }

        
        public void setAmount(final BigDecimal amount) {
            this.amount = amount;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof FinanceAssetController.BatchImpairReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$amount = this.getAmount();
                Object other$amount = other.getAmount();
                return this$amount == null ? other$amount == null : this$amount.equals(other$amount);
            }
        }

        
        @Override
        protected boolean canEqual(final Object other) {
            return other instanceof FinanceAssetController.BatchImpairReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $amount = this.getAmount();
            return result * 59 + ($amount == null ? 43 : $amount.hashCode());
        }

        
        @Override
        public String toString() {
            return "FinanceAssetController.BatchImpairReq(amount=" + this.getAmount() + ")";
        }
    }

    public static class BatchItem {
        private String assetNo;
        private String name;
        private String to;
        private String reason;

        static FinanceAssetController.BatchItem done(FinanceAssetVO vo) {
            FinanceAssetController.BatchItem i = new FinanceAssetController.BatchItem();
            i.setAssetNo(vo.getAssetNo());
            i.setName(vo.getAssetName());
            i.setTo(vo.getStatusFin());
            return i;
        }

        static FinanceAssetController.BatchItem skipped(String assetNo, String reason) {
            FinanceAssetController.BatchItem i = new FinanceAssetController.BatchItem();
            i.setAssetNo(assetNo);
            i.setReason(reason);
            return i;
        }

        
        public String getAssetNo() {
            return this.assetNo;
        }

        
        public String getName() {
            return this.name;
        }

        
        public String getTo() {
            return this.to;
        }

        
        public String getReason() {
            return this.reason;
        }

        
        public void setAssetNo(final String assetNo) {
            this.assetNo = assetNo;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setTo(final String to) {
            this.to = to;
        }

        
        public void setReason(final String reason) {
            this.reason = reason;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof FinanceAssetController.BatchItem other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$assetNo = this.getAssetNo();
                Object other$assetNo = other.getAssetNo();
                if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                    Object this$name = this.getName();
                    Object other$name = other.getName();
                    if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                        Object this$to = this.getTo();
                        Object other$to = other.getTo();
                        if (this$to == null ? other$to == null : this$to.equals(other$to)) {
                            Object this$reason = this.getReason();
                            Object other$reason = other.getReason();
                            return this$reason == null ? other$reason == null : this$reason.equals(other$reason);
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
            return other instanceof FinanceAssetController.BatchItem;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $assetNo = this.getAssetNo();
            result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $to = this.getTo();
            result = result * 59 + ($to == null ? 43 : $to.hashCode());
            Object $reason = this.getReason();
            return result * 59 + ($reason == null ? 43 : $reason.hashCode());
        }

        
        @Override
        public String toString() {
            return "FinanceAssetController.BatchItem(assetNo="
                + this.getAssetNo()
                + ", name="
                + this.getName()
                + ", to="
                + this.getTo()
                + ", reason="
                + this.getReason()
                + ")";
        }
    }

    public static class BatchNosReq {
        @NotEmpty(
            message = "资产编号列表不能为空"
        )
        private List<String> assetNos;

        
        public List<String> getAssetNos() {
            return this.assetNos;
        }

        
        public void setAssetNos(final List<String> assetNos) {
            this.assetNos = assetNos;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof FinanceAssetController.BatchNosReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$assetNos = this.getAssetNos();
                Object other$assetNos = other.getAssetNos();
                return this$assetNos == null ? other$assetNos == null : this$assetNos.equals(other$assetNos);
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof FinanceAssetController.BatchNosReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $assetNos = this.getAssetNos();
            return result * 59 + ($assetNos == null ? 43 : $assetNos.hashCode());
        }

        
        @Override
        public String toString() {
            return "FinanceAssetController.BatchNosReq(assetNos=" + this.getAssetNos() + ")";
        }
    }

    public static class BatchRegisterReq {
        @NotEmpty(
            message = "资产编号列表不能为空"
        )
        private List<String> assetNos;
        private BigDecimal originValue;
        private BigDecimal residualRate;
        private Integer lifeMonths;
        private String depMethod;
        private String depStartDate;
        private Boolean leaseFlag;

        
        public List<String> getAssetNos() {
            return this.assetNos;
        }

        
        public BigDecimal getOriginValue() {
            return this.originValue;
        }

        
        public BigDecimal getResidualRate() {
            return this.residualRate;
        }

        
        public Integer getLifeMonths() {
            return this.lifeMonths;
        }

        
        public String getDepMethod() {
            return this.depMethod;
        }

        
        public String getDepStartDate() {
            return this.depStartDate;
        }

        
        public Boolean getLeaseFlag() {
            return this.leaseFlag;
        }

        
        public void setAssetNos(final List<String> assetNos) {
            this.assetNos = assetNos;
        }

        
        public void setOriginValue(final BigDecimal originValue) {
            this.originValue = originValue;
        }

        
        public void setResidualRate(final BigDecimal residualRate) {
            this.residualRate = residualRate;
        }

        
        public void setLifeMonths(final Integer lifeMonths) {
            this.lifeMonths = lifeMonths;
        }

        
        public void setDepMethod(final String depMethod) {
            this.depMethod = depMethod;
        }

        
        public void setDepStartDate(final String depStartDate) {
            this.depStartDate = depStartDate;
        }

        
        public void setLeaseFlag(final Boolean leaseFlag) {
            this.leaseFlag = leaseFlag;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof FinanceAssetController.BatchRegisterReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$lifeMonths = this.getLifeMonths();
                Object other$lifeMonths = other.getLifeMonths();
                if (this$lifeMonths == null ? other$lifeMonths == null : this$lifeMonths.equals(other$lifeMonths)) {
                    Object this$leaseFlag = this.getLeaseFlag();
                    Object other$leaseFlag = other.getLeaseFlag();
                    if (this$leaseFlag == null ? other$leaseFlag == null : this$leaseFlag.equals(other$leaseFlag)) {
                        Object this$assetNos = this.getAssetNos();
                        Object other$assetNos = other.getAssetNos();
                        if (this$assetNos == null ? other$assetNos == null : this$assetNos.equals(other$assetNos)) {
                            Object this$originValue = this.getOriginValue();
                            Object other$originValue = other.getOriginValue();
                            if (this$originValue == null ? other$originValue == null : this$originValue.equals(other$originValue)) {
                                Object this$residualRate = this.getResidualRate();
                                Object other$residualRate = other.getResidualRate();
                                if (this$residualRate == null ? other$residualRate == null : this$residualRate.equals(other$residualRate)) {
                                    Object this$depMethod = this.getDepMethod();
                                    Object other$depMethod = other.getDepMethod();
                                    if (this$depMethod == null ? other$depMethod == null : this$depMethod.equals(other$depMethod)) {
                                        Object this$depStartDate = this.getDepStartDate();
                                        Object other$depStartDate = other.getDepStartDate();
                                        return this$depStartDate == null ? other$depStartDate == null : this$depStartDate.equals(other$depStartDate);
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
            return other instanceof FinanceAssetController.BatchRegisterReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $lifeMonths = this.getLifeMonths();
            result = result * 59 + ($lifeMonths == null ? 43 : $lifeMonths.hashCode());
            Object $leaseFlag = this.getLeaseFlag();
            result = result * 59 + ($leaseFlag == null ? 43 : $leaseFlag.hashCode());
            Object $assetNos = this.getAssetNos();
            result = result * 59 + ($assetNos == null ? 43 : $assetNos.hashCode());
            Object $originValue = this.getOriginValue();
            result = result * 59 + ($originValue == null ? 43 : $originValue.hashCode());
            Object $residualRate = this.getResidualRate();
            result = result * 59 + ($residualRate == null ? 43 : $residualRate.hashCode());
            Object $depMethod = this.getDepMethod();
            result = result * 59 + ($depMethod == null ? 43 : $depMethod.hashCode());
            Object $depStartDate = this.getDepStartDate();
            return result * 59 + ($depStartDate == null ? 43 : $depStartDate.hashCode());
        }

        
        @Override
        public String toString() {
            return "FinanceAssetController.BatchRegisterReq(assetNos="
                + this.getAssetNos()
                + ", originValue="
                + this.getOriginValue()
                + ", residualRate="
                + this.getResidualRate()
                + ", lifeMonths="
                + this.getLifeMonths()
                + ", depMethod="
                + this.getDepMethod()
                + ", depStartDate="
                + this.getDepStartDate()
                + ", leaseFlag="
                + this.getLeaseFlag()
                + ")";
        }
    }

    public static class BatchResult {
        private int total;
        private List<FinanceAssetController.BatchItem> changed = new ArrayList<>();
        private List<FinanceAssetController.BatchItem> skipped = new ArrayList<>();

        
        public int getTotal() {
            return this.total;
        }

        
        public List<FinanceAssetController.BatchItem> getChanged() {
            return this.changed;
        }

        
        public List<FinanceAssetController.BatchItem> getSkipped() {
            return this.skipped;
        }

        
        public void setTotal(final int total) {
            this.total = total;
        }

        
        public void setChanged(final List<FinanceAssetController.BatchItem> changed) {
            this.changed = changed;
        }

        
        public void setSkipped(final List<FinanceAssetController.BatchItem> skipped) {
            this.skipped = skipped;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof FinanceAssetController.BatchResult other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else if (this.getTotal() != other.getTotal()) {
                return false;
            } else {
                Object this$changed = this.getChanged();
                Object other$changed = other.getChanged();
                if (this$changed == null ? other$changed == null : this$changed.equals(other$changed)) {
                    Object this$skipped = this.getSkipped();
                    Object other$skipped = other.getSkipped();
                    return this$skipped == null ? other$skipped == null : this$skipped.equals(other$skipped);
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof FinanceAssetController.BatchResult;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            result = result * 59 + this.getTotal();
            Object $changed = this.getChanged();
            result = result * 59 + ($changed == null ? 43 : $changed.hashCode());
            Object $skipped = this.getSkipped();
            return result * 59 + ($skipped == null ? 43 : $skipped.hashCode());
        }

        
        @Override
        public String toString() {
            return "FinanceAssetController.BatchResult(total=" + this.getTotal() + ", changed=" + this.getChanged() + ", skipped=" + this.getSkipped() + ")";
        }
    }

    public static class ImpairmentReq {
        private BigDecimal amount;

        
        public BigDecimal getAmount() {
            return this.amount;
        }

        
        public void setAmount(final BigDecimal amount) {
            this.amount = amount;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof FinanceAssetController.ImpairmentReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$amount = this.getAmount();
                Object other$amount = other.getAmount();
                return this$amount == null ? other$amount == null : this$amount.equals(other$amount);
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof FinanceAssetController.ImpairmentReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $amount = this.getAmount();
            return result * 59 + ($amount == null ? 43 : $amount.hashCode());
        }

        
        @Override
        public String toString() {
            return "FinanceAssetController.ImpairmentReq(amount=" + this.getAmount() + ")";
        }
    }

    public static class RegisterReq {
        @NotBlank(
            message = "资产编号不能为空"
        )
        private String assetNo;
        private BigDecimal originValue;
        private BigDecimal residualRate;
        private Integer lifeMonths;
        private String depMethod;
        private String depStartDate;
        private Boolean leaseFlag;

        
        public String getAssetNo() {
            return this.assetNo;
        }

        
        public BigDecimal getOriginValue() {
            return this.originValue;
        }

        
        public BigDecimal getResidualRate() {
            return this.residualRate;
        }

        
        public Integer getLifeMonths() {
            return this.lifeMonths;
        }

        
        public String getDepMethod() {
            return this.depMethod;
        }

        
        public String getDepStartDate() {
            return this.depStartDate;
        }

        
        public Boolean getLeaseFlag() {
            return this.leaseFlag;
        }

        
        public void setAssetNo(final String assetNo) {
            this.assetNo = assetNo;
        }

        
        public void setOriginValue(final BigDecimal originValue) {
            this.originValue = originValue;
        }

        
        public void setResidualRate(final BigDecimal residualRate) {
            this.residualRate = residualRate;
        }

        
        public void setLifeMonths(final Integer lifeMonths) {
            this.lifeMonths = lifeMonths;
        }

        
        public void setDepMethod(final String depMethod) {
            this.depMethod = depMethod;
        }

        
        public void setDepStartDate(final String depStartDate) {
            this.depStartDate = depStartDate;
        }

        
        public void setLeaseFlag(final Boolean leaseFlag) {
            this.leaseFlag = leaseFlag;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof FinanceAssetController.RegisterReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$lifeMonths = this.getLifeMonths();
                Object other$lifeMonths = other.getLifeMonths();
                if (this$lifeMonths == null ? other$lifeMonths == null : this$lifeMonths.equals(other$lifeMonths)) {
                    Object this$leaseFlag = this.getLeaseFlag();
                    Object other$leaseFlag = other.getLeaseFlag();
                    if (this$leaseFlag == null ? other$leaseFlag == null : this$leaseFlag.equals(other$leaseFlag)) {
                        Object this$assetNo = this.getAssetNo();
                        Object other$assetNo = other.getAssetNo();
                        if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                            Object this$originValue = this.getOriginValue();
                            Object other$originValue = other.getOriginValue();
                            if (this$originValue == null ? other$originValue == null : this$originValue.equals(other$originValue)) {
                                Object this$residualRate = this.getResidualRate();
                                Object other$residualRate = other.getResidualRate();
                                if (this$residualRate == null ? other$residualRate == null : this$residualRate.equals(other$residualRate)) {
                                    Object this$depMethod = this.getDepMethod();
                                    Object other$depMethod = other.getDepMethod();
                                    if (this$depMethod == null ? other$depMethod == null : this$depMethod.equals(other$depMethod)) {
                                        Object this$depStartDate = this.getDepStartDate();
                                        Object other$depStartDate = other.getDepStartDate();
                                        return this$depStartDate == null ? other$depStartDate == null : this$depStartDate.equals(other$depStartDate);
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
            return other instanceof FinanceAssetController.RegisterReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $lifeMonths = this.getLifeMonths();
            result = result * 59 + ($lifeMonths == null ? 43 : $lifeMonths.hashCode());
            Object $leaseFlag = this.getLeaseFlag();
            result = result * 59 + ($leaseFlag == null ? 43 : $leaseFlag.hashCode());
            Object $assetNo = this.getAssetNo();
            result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
            Object $originValue = this.getOriginValue();
            result = result * 59 + ($originValue == null ? 43 : $originValue.hashCode());
            Object $residualRate = this.getResidualRate();
            result = result * 59 + ($residualRate == null ? 43 : $residualRate.hashCode());
            Object $depMethod = this.getDepMethod();
            result = result * 59 + ($depMethod == null ? 43 : $depMethod.hashCode());
            Object $depStartDate = this.getDepStartDate();
            return result * 59 + ($depStartDate == null ? 43 : $depStartDate.hashCode());
        }

        
        @Override
        public String toString() {
            return "FinanceAssetController.RegisterReq(assetNo="
                + this.getAssetNo()
                + ", originValue="
                + this.getOriginValue()
                + ", residualRate="
                + this.getResidualRate()
                + ", lifeMonths="
                + this.getLifeMonths()
                + ", depMethod="
                + this.getDepMethod()
                + ", depStartDate="
                + this.getDepStartDate()
                + ", leaseFlag="
                + this.getLeaseFlag()
                + ")";
        }
    }
}
