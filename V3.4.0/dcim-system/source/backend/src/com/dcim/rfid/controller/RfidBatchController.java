package com.dcim.rfid.controller;

import com.dcim.common.ApiResult;
import com.dcim.common.PageResult;
import com.dcim.rfid.service.RfidBatchService;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/rfid/batches"})
public class RfidBatchController {
    private final RfidBatchService service;

    public RfidBatchController(RfidBatchService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePerm(
        module = "rfid",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "rfid",
        objType = "rfidBatch"
    )
    public ApiResult<Map<String, Object>> create(@RequestBody @Valid RfidBatchController.CreateBatchReq req) {
        return ApiResult.ok(this.service.create(req.getName(), req.getSupplierCode(), req.getSourceType(), req.getRemark()));
    }

    @GetMapping
    @RequirePerm(
        module = "rfid"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword
    ) {
        return ApiResult.ok(this.service.list(page, size, status, keyword));
    }

    @GetMapping({"/{batchNo}"})
    @RequirePerm(
        module = "rfid"
    )
    public ApiResult<Map<String, Object>> detail(@PathVariable String batchNo) {
        return ApiResult.ok(this.service.detail(batchNo));
    }

    @GetMapping({"/{batchNo}/scans"})
    @RequirePerm(
        module = "rfid"
    )
    public ApiResult<PageResult<Map<String, Object>>> scans(
        @PathVariable String batchNo,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String scanStatus
    ) {
        return ApiResult.ok(this.service.scans(batchNo, page, size, scanStatus));
    }

    @PostMapping({"/{batchNo}/items"})
    @RequirePerm(
        module = "rfid",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "rfid",
        objType = "rfidBatch",
        objIdSpEL = "#batchNo"
    )
    public ApiResult<Map<String, Object>> addItem(@PathVariable String batchNo, @RequestBody @Valid RfidBatchController.AddItemReq req) {
        return ApiResult.ok(this.service.addItem(batchNo, req.getMaterialCode(), req.getExpectedQty()));
    }

    @PutMapping({"/{batchNo}/items/{id}"})
    @RequirePerm(
        module = "rfid",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "rfid",
        objType = "rfidBatch",
        objIdSpEL = "#batchNo"
    )
    public ApiResult<Map<String, Object>> updateItem(
        @PathVariable String batchNo, @PathVariable Long id, @RequestBody @Valid RfidBatchController.UpdateItemReq req
    ) {
        return ApiResult.ok(this.service.updateItem(batchNo, id, req.getExpectedQty()));
    }

    @DeleteMapping({"/{batchNo}/items/{id}"})
    @RequirePerm(
        module = "rfid",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "rfid",
        objType = "rfidBatch",
        objIdSpEL = "#batchNo"
    )
    public ApiResult<Void> deleteItem(@PathVariable String batchNo, @PathVariable Long id) {
        this.service.deleteItem(batchNo, id);
        return ApiResult.ok();
    }

    @PostMapping(
        value = {"/{batchNo}/items/import"},
        consumes = {"multipart/form-data"}
    )
    @RequirePerm(
        module = "rfid",
        write = true,
        action = "import"
    )
    @Audit(
        action = "IMPORT",
        module = "rfid",
        objType = "rfidBatch",
        objIdSpEL = "#batchNo"
    )
    public ApiResult<Map<String, Object>> importItems(@PathVariable String batchNo, @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResult.ok(this.service.importItems(batchNo, file));
    }

    @PostMapping({"/{batchNo}/start"})
    @RequirePerm(
        module = "rfid",
        write = true,
        action = "update"
    )
    @Audit(
        action = "START",
        module = "rfid",
        objType = "rfidBatch",
        objIdSpEL = "#batchNo"
    )
    public ApiResult<Map<String, Object>> start(@PathVariable String batchNo) {
        return ApiResult.ok(this.service.start(batchNo));
    }

    @PostMapping({"/{batchNo}/scan"})
    @RequirePerm(
        module = "rfid",
        write = true,
        action = "update"
    )
    @Audit(
        action = "SCAN",
        module = "rfid",
        objType = "rfidBatch",
        objIdSpEL = "#batchNo"
    )
    public ApiResult<Map<String, Object>> scan(@PathVariable String batchNo, @RequestBody @Valid RfidBatchController.ScanReq req) {
        return ApiResult.ok(this.service.scan(batchNo, req.getEpc(), req.getSn(), req.getMaterialCode(), req.getExceptionNote()));
    }

    @PostMapping({"/{batchNo}/confirm"})
    @RequirePerm(
        module = "rfid",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "CONFIRM",
        module = "rfid",
        objType = "rfidBatch",
        objIdSpEL = "#batchNo"
    )
    public ApiResult<Map<String, Object>> confirm(@PathVariable String batchNo, @RequestBody(required = false) RfidBatchController.ConfirmReq req) {
        RfidBatchController.ConfirmReq body = req == null ? new RfidBatchController.ConfirmReq() : req;
        return ApiResult.ok(
            this.service
                .confirm(
                    batchNo, body.getNamePrefix(), body.getCategoryCode(), body.getModelCode(), body.getOwnerType(), body.getRegionCode(), body.getRemark()
                )
        );
    }

    @PostMapping({"/{batchNo}/cancel"})
    @RequirePerm(
        module = "rfid",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "CANCEL",
        module = "rfid",
        objType = "rfidBatch",
        objIdSpEL = "#batchNo"
    )
    public ApiResult<Map<String, Object>> cancel(@PathVariable String batchNo) {
        return ApiResult.ok(this.service.cancel(batchNo));
    }

    public static class AddItemReq {
        @NotBlank(
            message = "物料编码不能为空"
        )
        private String materialCode;
        @NotNull(
            message = "预期数量不能为空"
        )
        @Min(
            value = 1L,
            message = "预期数量必须为正整数"
        )
        private Integer expectedQty;

        
        public String getMaterialCode() {
            return this.materialCode;
        }

        
        public Integer getExpectedQty() {
            return this.expectedQty;
        }

        
        public void setMaterialCode(final String materialCode) {
            this.materialCode = materialCode;
        }

        
        public void setExpectedQty(final Integer expectedQty) {
            this.expectedQty = expectedQty;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof RfidBatchController.AddItemReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$expectedQty = this.getExpectedQty();
                Object other$expectedQty = other.getExpectedQty();
                if (this$expectedQty == null ? other$expectedQty == null : this$expectedQty.equals(other$expectedQty)) {
                    Object this$materialCode = this.getMaterialCode();
                    Object other$materialCode = other.getMaterialCode();
                    return this$materialCode == null ? other$materialCode == null : this$materialCode.equals(other$materialCode);
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof RfidBatchController.AddItemReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $expectedQty = this.getExpectedQty();
            result = result * 59 + ($expectedQty == null ? 43 : $expectedQty.hashCode());
            Object $materialCode = this.getMaterialCode();
            return result * 59 + ($materialCode == null ? 43 : $materialCode.hashCode());
        }

        
        @Override
        public String toString() {
            return "RfidBatchController.AddItemReq(materialCode=" + this.getMaterialCode() + ", expectedQty=" + this.getExpectedQty() + ")";
        }
    }

    public static class ConfirmReq {
        private String namePrefix;
        private String categoryCode;
        private String modelCode;
        private String regionCode;
        private String ownerType;
        private String remark;

        
        public String getNamePrefix() {
            return this.namePrefix;
        }

        
        public String getCategoryCode() {
            return this.categoryCode;
        }

        
        public String getModelCode() {
            return this.modelCode;
        }

        
        public String getRegionCode() {
            return this.regionCode;
        }

        
        public String getOwnerType() {
            return this.ownerType;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public void setNamePrefix(final String namePrefix) {
            this.namePrefix = namePrefix;
        }

        
        public void setCategoryCode(final String categoryCode) {
            this.categoryCode = categoryCode;
        }

        
        public void setModelCode(final String modelCode) {
            this.modelCode = modelCode;
        }

        
        public void setRegionCode(final String regionCode) {
            this.regionCode = regionCode;
        }

        
        public void setOwnerType(final String ownerType) {
            this.ownerType = ownerType;
        }

        
        public void setRemark(final String remark) {
            this.remark = remark;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof RfidBatchController.ConfirmReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$namePrefix = this.getNamePrefix();
                Object other$namePrefix = other.getNamePrefix();
                if (this$namePrefix == null ? other$namePrefix == null : this$namePrefix.equals(other$namePrefix)) {
                    Object this$categoryCode = this.getCategoryCode();
                    Object other$categoryCode = other.getCategoryCode();
                    if (this$categoryCode == null ? other$categoryCode == null : this$categoryCode.equals(other$categoryCode)) {
                        Object this$modelCode = this.getModelCode();
                        Object other$modelCode = other.getModelCode();
                        if (this$modelCode == null ? other$modelCode == null : this$modelCode.equals(other$modelCode)) {
                            Object this$regionCode = this.getRegionCode();
                            Object other$regionCode = other.getRegionCode();
                            if (this$regionCode == null ? other$regionCode == null : this$regionCode.equals(other$regionCode)) {
                                Object this$ownerType = this.getOwnerType();
                                Object other$ownerType = other.getOwnerType();
                                if (this$ownerType == null ? other$ownerType == null : this$ownerType.equals(other$ownerType)) {
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
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof RfidBatchController.ConfirmReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $namePrefix = this.getNamePrefix();
            result = result * 59 + ($namePrefix == null ? 43 : $namePrefix.hashCode());
            Object $categoryCode = this.getCategoryCode();
            result = result * 59 + ($categoryCode == null ? 43 : $categoryCode.hashCode());
            Object $modelCode = this.getModelCode();
            result = result * 59 + ($modelCode == null ? 43 : $modelCode.hashCode());
            Object $regionCode = this.getRegionCode();
            result = result * 59 + ($regionCode == null ? 43 : $regionCode.hashCode());
            Object $ownerType = this.getOwnerType();
            result = result * 59 + ($ownerType == null ? 43 : $ownerType.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "RfidBatchController.ConfirmReq(namePrefix="
                + this.getNamePrefix()
                + ", categoryCode="
                + this.getCategoryCode()
                + ", modelCode="
                + this.getModelCode()
                + ", regionCode="
                + this.getRegionCode()
                + ", ownerType="
                + this.getOwnerType()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }

    public static class CreateBatchReq {
        @NotBlank(
            message = "批次名称不能为空"
        )
        private String name;
        private String supplierCode;
        private String sourceType;
        private String remark;

        
        public String getName() {
            return this.name;
        }

        
        public String getSupplierCode() {
            return this.supplierCode;
        }

        
        public String getSourceType() {
            return this.sourceType;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setSupplierCode(final String supplierCode) {
            this.supplierCode = supplierCode;
        }

        
        public void setSourceType(final String sourceType) {
            this.sourceType = sourceType;
        }

        
        public void setRemark(final String remark) {
            this.remark = remark;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof RfidBatchController.CreateBatchReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$name = this.getName();
                Object other$name = other.getName();
                if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                    Object this$supplierCode = this.getSupplierCode();
                    Object other$supplierCode = other.getSupplierCode();
                    if (this$supplierCode == null ? other$supplierCode == null : this$supplierCode.equals(other$supplierCode)) {
                        Object this$sourceType = this.getSourceType();
                        Object other$sourceType = other.getSourceType();
                        if (this$sourceType == null ? other$sourceType == null : this$sourceType.equals(other$sourceType)) {
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
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof RfidBatchController.CreateBatchReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $supplierCode = this.getSupplierCode();
            result = result * 59 + ($supplierCode == null ? 43 : $supplierCode.hashCode());
            Object $sourceType = this.getSourceType();
            result = result * 59 + ($sourceType == null ? 43 : $sourceType.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "RfidBatchController.CreateBatchReq(name="
                + this.getName()
                + ", supplierCode="
                + this.getSupplierCode()
                + ", sourceType="
                + this.getSourceType()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }

    public static class ScanReq {
        @NotBlank(
            message = "EPC 不能为空"
        )
        private String epc;
        private String sn;
        private String materialCode;
        private String exceptionNote;

        
        public String getEpc() {
            return this.epc;
        }

        
        public String getSn() {
            return this.sn;
        }

        
        public String getMaterialCode() {
            return this.materialCode;
        }

        
        public String getExceptionNote() {
            return this.exceptionNote;
        }

        
        public void setEpc(final String epc) {
            this.epc = epc;
        }

        
        public void setSn(final String sn) {
            this.sn = sn;
        }

        
        public void setMaterialCode(final String materialCode) {
            this.materialCode = materialCode;
        }

        
        public void setExceptionNote(final String exceptionNote) {
            this.exceptionNote = exceptionNote;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof RfidBatchController.ScanReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$epc = this.getEpc();
                Object other$epc = other.getEpc();
                if (this$epc == null ? other$epc == null : this$epc.equals(other$epc)) {
                    Object this$sn = this.getSn();
                    Object other$sn = other.getSn();
                    if (this$sn == null ? other$sn == null : this$sn.equals(other$sn)) {
                        Object this$materialCode = this.getMaterialCode();
                        Object other$materialCode = other.getMaterialCode();
                        if (this$materialCode == null ? other$materialCode == null : this$materialCode.equals(other$materialCode)) {
                            Object this$exceptionNote = this.getExceptionNote();
                            Object other$exceptionNote = other.getExceptionNote();
                            return this$exceptionNote == null ? other$exceptionNote == null : this$exceptionNote.equals(other$exceptionNote);
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
            return other instanceof RfidBatchController.ScanReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $epc = this.getEpc();
            result = result * 59 + ($epc == null ? 43 : $epc.hashCode());
            Object $sn = this.getSn();
            result = result * 59 + ($sn == null ? 43 : $sn.hashCode());
            Object $materialCode = this.getMaterialCode();
            result = result * 59 + ($materialCode == null ? 43 : $materialCode.hashCode());
            Object $exceptionNote = this.getExceptionNote();
            return result * 59 + ($exceptionNote == null ? 43 : $exceptionNote.hashCode());
        }

        
        @Override
        public String toString() {
            return "RfidBatchController.ScanReq(epc="
                + this.getEpc()
                + ", sn="
                + this.getSn()
                + ", materialCode="
                + this.getMaterialCode()
                + ", exceptionNote="
                + this.getExceptionNote()
                + ")";
        }
    }

    public static class UpdateItemReq {
        @NotNull(
            message = "预期数量不能为空"
        )
        @Min(
            value = 1L,
            message = "预期数量必须为正整数"
        )
        private Integer expectedQty;

        
        public Integer getExpectedQty() {
            return this.expectedQty;
        }

        
        public void setExpectedQty(final Integer expectedQty) {
            this.expectedQty = expectedQty;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof RfidBatchController.UpdateItemReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$expectedQty = this.getExpectedQty();
                Object other$expectedQty = other.getExpectedQty();
                return this$expectedQty == null ? other$expectedQty == null : this$expectedQty.equals(other$expectedQty);
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof RfidBatchController.UpdateItemReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $expectedQty = this.getExpectedQty();
            return result * 59 + ($expectedQty == null ? 43 : $expectedQty.hashCode());
        }

        
        @Override
        public String toString() {
            return "RfidBatchController.UpdateItemReq(expectedQty=" + this.getExpectedQty() + ")";
        }
    }
}
