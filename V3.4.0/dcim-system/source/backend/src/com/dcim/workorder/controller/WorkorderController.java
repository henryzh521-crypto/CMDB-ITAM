package com.dcim.workorder.controller;

import com.dcim.common.ApiResult;
import com.dcim.common.PageResult;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import com.dcim.workorder.entity.WorkorderEntity;
import com.dcim.workorder.service.WoAssetLineInput;
import com.dcim.workorder.service.WoListQuery;
import com.dcim.workorder.service.WorkorderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
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
@RequestMapping({"/api/workorders"})
public class WorkorderController {
    @org.springframework.beans.factory.annotation.Autowired
    private com.dcim.security.field.FieldPolicyService fieldPolicies;
    private final WorkorderService workorderService;
    static final long MAX_PAGE_SIZE = 200L;

    static long clampSize(long size) {
        return size < 1L ? 20L : Math.min(size, 200L);
    }

    @GetMapping
    @RequirePerm(
        module = "workorders"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String woType,
        @RequestParam(required = false) String priority,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String tab,
        @RequestParam(required = false) String handler,
        @RequestParam(required = false) String applyFrom,
        @RequestParam(required = false) String applyTo,
        @RequestParam(required = false) Boolean slaOverdue
    ) {
        WoListQuery q = new WoListQuery();
        q.setPage(Math.max(1L, page));
        q.setSize(clampSize(size));
        q.setStatus(status);
        q.setWoType(woType);
        q.setPriority(priority);
        q.setKeyword(keyword);
        q.setTab(tab);
        q.setHandler(handler);
        q.setApplyFrom(applyFrom);
        q.setApplyTo(applyTo);
        q.setSlaOverdue(slaOverdue);
        return ApiResult.ok(this.workorderService.list(q));
    }

    @GetMapping({"/export"})
    @RequirePerm(
        module = "workorders",
        action = "export"
    )
    public ResponseEntity<byte[]> export(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String woType,
        @RequestParam(required = false) String priority,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String handler,
        @RequestParam(required = false) String applyFrom,
        @RequestParam(required = false) String applyTo,
        @RequestParam(required = false) Boolean slaOverdue
    ) {
        WoListQuery q = new WoListQuery();
        q.setStatus(status);
        q.setWoType(woType);
        q.setPriority(priority);
        q.setKeyword(keyword);
        q.setHandler(handler);
        q.setApplyFrom(applyFrom);
        q.setApplyTo(applyTo);
        q.setSlaOverdue(slaOverdue);
        // v3.4：与资产导出一致，受角色「导出」操作权限约束，并按字段权限过滤列
        String csv = CsvExport.render(
            this.workorderService.exportRows(q),
            this.fieldPolicies.policy(com.dcim.security.CurrentUser.get(), "workorders").hiddenKeysLower("workorders")
        );
        String filename = "workorders-" + LocalDate.now() + ".csv";
        byte[] body = ("\ufeff" + csv).getBytes(StandardCharsets.UTF_8);
        return ((BodyBuilder)ResponseEntity.ok().header("Content-Disposition", new String[]{"attachment; filename=\"" + filename + "\""}))
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(body);
    }

    @GetMapping({"/{woNo}"})
    @RequirePerm(
        module = "workorders"
    )
    public ApiResult<Map<String, Object>> detail(@PathVariable String woNo) {
        return ApiResult.ok(this.workorderService.detail(woNo));
    }

    @PostMapping
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "workorders",
        objType = "workorder"
    )
    public ApiResult<Map<String, Object>> create(@RequestBody @Valid WorkorderController.CreateWoReq req) {
        WoAssetJson.rejectUnknownAssetFields(req.unknownFields());
        List<WoAssetLineInput> assetLines = this.toAssetLines(req);
        WorkorderEntity wo = this.workorderService
            .create(
                req.getWoType(),
                req.getTitle(),
                req.getDescription(),
                req.getPriority(),
                assetLines,
                req.getRelatedMaterials(),
                req.getApplyQty(),
                req.getTargetLocation(),
                req.getTargetRack(),
                req.getTargetUnit(),
                WorkorderService.parseDate(req.getExpectFinish()),
                this.toObjectList(req.getDetails()),
                req.getParentWo(),
                req.getReplacedAsset(),
                this.toReplacements(req.getReplacements()),
                req.getApprovalApprovers()
            );
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("woNo", wo.getWoNo());
        out.put("status", wo.getStatus());
        out.put("slaResponseDeadline", wo.getSlaResponseDeadline());
        out.put("slaResolveDeadline", wo.getSlaResolveDeadline());
        return ApiResult.ok(out);
    }

    @PostMapping({"/{woNo}/transitions"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "update"
    )
    @Audit(
        action = "TRANSITION",
        module = "workorders",
        objType = "workorder",
        objIdSpEL = "#woNo"
    )
    public ApiResult<Map<String, Object>> transition(@PathVariable String woNo, @RequestBody @Valid WorkorderController.TransitionReq req) {
        return ApiResult.ok(
            this.workorderService
                .transition(
                    woNo,
                    req.getAction(),
                    req.getOpinion(),
                    req.getRootCause(),
                    req.getReplacedAsset(),
                    req.getCompleteNote(),
                    req.getCostLabor(),
                    req.getCostService(),
                    req.getCostOther()
                )
        );
    }

    @PutMapping({"/{woNo}/repair-cost"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "workorders",
        objType = "workorder",
        objIdSpEL = "#woNo"
    )
    public ApiResult<Map<String, Object>> repairCost(@PathVariable String woNo, @RequestBody @Valid WorkorderController.RepairCostReq req) {
        return ApiResult.ok(this.workorderService.updateRepairCost(woNo, req.getCostLabor(), req.getCostService(), req.getCostOther()));
    }

    @PutMapping({"/{woNo}"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "workorders",
        objType = "workorder",
        objIdSpEL = "#woNo"
    )
    public ApiResult<Map<String, Object>> update(@PathVariable String woNo, @RequestBody @Valid WorkorderController.UpdateWoReq req) {
        WoAssetJson.rejectUnknownAssetFields(req.unknownFields());
        if (req.getRelatedAssets() != null) {
            return ApiResult.ok(
                this.workorderService
                    .updateBasicAndAssets(woNo, req.getTitle(), req.getDescription(), req.getPriority(), req.getExpectFinish(), req.getRelatedAssets())
            );
        }
        return ApiResult.ok(this.workorderService.updateBasic(woNo, req.getTitle(), req.getDescription(), req.getPriority(), req.getExpectFinish()));
    }

    @PostMapping({"/{woNo}/remind"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "update"
    )
    @Audit(
        action = "REMIND",
        module = "workorders",
        objType = "workorder",
        objIdSpEL = "#woNo"
    )
    public ApiResult<Map<String, Object>> remind(@PathVariable String woNo, @RequestBody(required = false) WorkorderController.RemindReq req) {
        return ApiResult.ok(this.workorderService.remind(woNo, req == null ? null : req.getOpinion()));
    }

    @PostMapping({"/{woNo}/comments"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "update"
    )
    @Audit(
        action = "COMMENT",
        module = "workorders",
        objType = "workorder",
        objIdSpEL = "#woNo"
    )
    public ApiResult<Map<String, Object>> comment(@PathVariable String woNo, @RequestBody @Valid WorkorderController.CommentReq req) {
        return ApiResult.ok(this.workorderService.comment(woNo, req.getContent()));
    }

    @GetMapping({"/workflow-config"})
    @RequirePerm(
        module = "workorders"
    )
    public ApiResult<List<Map<String, Object>>> workflowConfig() {
        return ApiResult.ok(this.workorderService.workflowConfig());
    }

    @PutMapping({"/workflow-config/{woType}"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE_WORKFLOW",
        module = "workorders",
        objType = "workflow_config",
        objIdSpEL = "#woType"
    )
    public ApiResult<Map<String, Object>> updateWorkflowConfig(@PathVariable String woType, @RequestBody @Valid WorkorderController.WorkflowConfigUpdateReq req) {
        return ApiResult.ok(this.workorderService.workflowConfigUpdate(woType, req.isNeedApproval(), req.getApprovalRoles(), req.getApprovalUserLevels()));
    }

    @PostMapping({"/{woNo}/approval/transfer"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "update"
    )
    @Audit(
        action = "TRANSFER_APPROVAL",
        module = "workorders",
        objType = "workorder",
        objIdSpEL = "#woNo"
    )
    public ApiResult<Map<String, Object>> approvalTransfer(@PathVariable String woNo, @RequestBody @Valid WorkorderController.ApprovalTransferReq req) {
        return ApiResult.ok(this.workorderService.approvalTransfer(woNo, req.getUsername(), req.getOpinion()));
    }

    @PostMapping({"/{woNo}/approval/add"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "update"
    )
    @Audit(
        action = "ADD_APPROVER",
        module = "workorders",
        objType = "workorder",
        objIdSpEL = "#woNo"
    )
    public ApiResult<Map<String, Object>> approvalAdd(@PathVariable String woNo, @RequestBody @Valid WorkorderController.ApprovalAddReq req) {
        return ApiResult.ok(this.workorderService.approvalAdd(woNo, req.getUsernames(), req.getOpinion()));
    }

    @GetMapping({"/approver-options"})
    @RequirePerm(
        module = "workorders"
    )
    public ApiResult<List<Map<String, Object>>> approverOptions(@RequestParam(required = false) String keyword) {
        return ApiResult.ok(this.workorderService.approverOptions(keyword));
    }

    @GetMapping({"/sla-config"})
    @RequirePerm(
        module = "workorders"
    )
    public ApiResult<List<Map<String, Object>>> slaConfig() {
        return ApiResult.ok(this.workorderService.slaConfigList());
    }

    @PutMapping({"/sla-config/{woType}/{priority}"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE_SLA",
        module = "workorders",
        objType = "sla_config",
        objIdSpEL = "#woType + '/' + #priority"
    )
    public ApiResult<Map<String, Object>> updateSlaConfig(
        @PathVariable String woType, @PathVariable String priority, @RequestBody WorkorderController.SlaConfigUpdateReq req
    ) {
        return ApiResult.ok(this.workorderService.slaConfigUpdate(woType, priority, req.getResponseMinutes(), req.getResolveMinutes(), req.getEscalateTo()));
    }

    @GetMapping({"/transfer-locations"})
    @RequirePerm(
        module = "workorders"
    )
    public ApiResult<Map<String, Object>> transferLocations() {
        return ApiResult.ok(this.workorderService.transferLocations());
    }

    @PostMapping({"/{woNo}/items"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "update"
    )
    @Audit(
        action = "ADD_ITEM",
        module = "workorders",
        objType = "workorder",
        objIdSpEL = "#woNo"
    )
    public ApiResult<Map<String, Object>> addItem(@PathVariable String woNo, @RequestBody @Valid WorkorderController.ItemReq req) {
        return ApiResult.ok(this.workorderService.addItem(woNo, this.toLine(req)));
    }

    @PutMapping({"/{woNo}/items/{itemNo}"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE_ITEM",
        module = "workorders",
        objType = "workorder",
        objIdSpEL = "#woNo"
    )
    public ApiResult<Map<String, Object>> updateItem(
        @PathVariable String woNo, @PathVariable String itemNo, @RequestBody @Valid WorkorderController.ItemReq req
    ) {
        return ApiResult.ok(this.workorderService.updateItem(woNo, itemNo, this.toLine(req)));
    }

    @DeleteMapping({"/{woNo}/items/{itemNo}"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "update"
    )
    @Audit(
        action = "DELETE_ITEM",
        module = "workorders",
        objType = "workorder",
        objIdSpEL = "#woNo"
    )
    public ApiResult<Void> deleteItem(@PathVariable String woNo, @PathVariable String itemNo) {
        this.workorderService.deleteItem(woNo, itemNo);
        return ApiResult.ok(null);
    }

    @PutMapping({"/{woNo}/replacements"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE_REPLACEMENTS",
        module = "workorders",
        objType = "workorder",
        objIdSpEL = "#woNo"
    )
    public ApiResult<List<Map<String, Object>>> updateReplacements(@PathVariable String woNo, @RequestBody @Valid WorkorderController.ReplacementsReq req) {
        return ApiResult.ok(this.workorderService.updateReplacements(woNo, this.toReplacements(req.getReplacements())));
    }

    private List<Object> toObjectList(List<?> list) {
        return list == null ? null : new ArrayList<>((Collection<? extends Object>)list);
    }

    private List<Object> toReplacements(List<WorkorderController.ReplacementReq> reps) {
        List<Object> out = new ArrayList<>();
        if (reps == null) {
            return out;
        } else {
            for (WorkorderController.ReplacementReq r : reps) {
                if (r != null) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("parentAssetNo", r.getParentAssetNo());
                    m.put("oldAssetNo", r.getOldAssetNo());
                    m.put("newAssetNo", r.getNewAssetNo());
                    if (r.getOldStatus() != null && !r.getOldStatus().isBlank()) {
                        m.put("oldStatus", r.getOldStatus());
                    }

                    out.add(m);
                }
            }

            return out;
        }
    }

    private List<WoAssetLineInput> toAssetLines(WorkorderController.CreateWoReq req) {
        List<WoAssetLineInput> out = new ArrayList<>();
        if (req.getRelatedAssets() != null) {
            for (String a : req.getRelatedAssets()) {
                if (a != null && !a.isBlank()) {
                    WoAssetLineInput line = new WoAssetLineInput();
                    line.setAssetNo(a.trim());
                    out.add(line);
                }
            }
        }

        if (req.getAssets() != null) {
            for (WorkorderController.AssetLineReq ax : req.getAssets()) {
                if (ax != null && ax.getAssetNo() != null && !ax.getAssetNo().isBlank()) {
                    WoAssetLineInput line = new WoAssetLineInput();
                    line.setAssetNo(ax.getAssetNo().trim());
                    line.setDstRoom(ax.getDstRoom());
                    line.setDstRack(ax.getDstRack());
                    line.setDstUnit(ax.getDstUnit());
                    out.removeIf(x -> line.getAssetNo().equals(x.getAssetNo()));
                    out.add(line);
                }
            }
        }

        return out;
    }

    private WoAssetLineInput toLine(WorkorderController.ItemReq req) {
        WoAssetLineInput line = new WoAssetLineInput();
        line.setAssetNo(req.getAssetNo().trim());
        line.setDstRoom(req.getDstRoom());
        line.setDstRack(req.getDstRack());
        line.setDstUnit(req.getDstUnit());
        return line;
    }

    
    public WorkorderController(final WorkorderService workorderService) {
        this.workorderService = workorderService;
    }

    public static class ApprovalAddReq {
        @NotEmpty(
            message = "加签审批人不能为空"
        )
        private List<String> usernames;
        private String opinion;

        
        public List<String> getUsernames() {
            return this.usernames;
        }

        
        public String getOpinion() {
            return this.opinion;
        }

        
        public void setUsernames(final List<String> usernames) {
            this.usernames = usernames;
        }

        
        public void setOpinion(final String opinion) {
            this.opinion = opinion;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WorkorderController.ApprovalAddReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$usernames = this.getUsernames();
                Object other$usernames = other.getUsernames();
                if (this$usernames == null ? other$usernames == null : this$usernames.equals(other$usernames)) {
                    Object this$opinion = this.getOpinion();
                    Object other$opinion = other.getOpinion();
                    return this$opinion == null ? other$opinion == null : this$opinion.equals(other$opinion);
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof WorkorderController.ApprovalAddReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $usernames = this.getUsernames();
            result = result * 59 + ($usernames == null ? 43 : $usernames.hashCode());
            Object $opinion = this.getOpinion();
            return result * 59 + ($opinion == null ? 43 : $opinion.hashCode());
        }

        
        @Override
        public String toString() {
            return "WorkorderController.ApprovalAddReq(usernames=" + this.getUsernames() + ", opinion=" + this.getOpinion() + ")";
        }
    }

    public static class ApprovalTransferReq {
        @NotBlank(
            message = "转办目标审批人不能为空"
        )
        private String username;
        @NotBlank(
            message = "转办必须填写说明（opinion）"
        )
        private String opinion;

        
        public String getUsername() {
            return this.username;
        }

        
        public String getOpinion() {
            return this.opinion;
        }

        
        public void setUsername(final String username) {
            this.username = username;
        }

        
        public void setOpinion(final String opinion) {
            this.opinion = opinion;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WorkorderController.ApprovalTransferReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$username = this.getUsername();
                Object other$username = other.getUsername();
                if (this$username == null ? other$username == null : this$username.equals(other$username)) {
                    Object this$opinion = this.getOpinion();
                    Object other$opinion = other.getOpinion();
                    return this$opinion == null ? other$opinion == null : this$opinion.equals(other$opinion);
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof WorkorderController.ApprovalTransferReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $username = this.getUsername();
            result = result * 59 + ($username == null ? 43 : $username.hashCode());
            Object $opinion = this.getOpinion();
            return result * 59 + ($opinion == null ? 43 : $opinion.hashCode());
        }

        
        @Override
        public String toString() {
            return "WorkorderController.ApprovalTransferReq(username=" + this.getUsername() + ", opinion=" + this.getOpinion() + ")";
        }
    }

    public static class AssetLineReq {
        @NotBlank(
            message = "资产编号不能为空"
        )
        private String assetNo;
        private String dstRoom;
        private String dstRack;
        private Integer dstUnit;

        
        public String getAssetNo() {
            return this.assetNo;
        }

        
        public String getDstRoom() {
            return this.dstRoom;
        }

        
        public String getDstRack() {
            return this.dstRack;
        }

        
        public Integer getDstUnit() {
            return this.dstUnit;
        }

        
        public void setAssetNo(final String assetNo) {
            this.assetNo = assetNo;
        }

        
        public void setDstRoom(final String dstRoom) {
            this.dstRoom = dstRoom;
        }

        
        public void setDstRack(final String dstRack) {
            this.dstRack = dstRack;
        }

        
        public void setDstUnit(final Integer dstUnit) {
            this.dstUnit = dstUnit;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WorkorderController.AssetLineReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$dstUnit = this.getDstUnit();
                Object other$dstUnit = other.getDstUnit();
                if (this$dstUnit == null ? other$dstUnit == null : this$dstUnit.equals(other$dstUnit)) {
                    Object this$assetNo = this.getAssetNo();
                    Object other$assetNo = other.getAssetNo();
                    if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                        Object this$dstRoom = this.getDstRoom();
                        Object other$dstRoom = other.getDstRoom();
                        if (this$dstRoom == null ? other$dstRoom == null : this$dstRoom.equals(other$dstRoom)) {
                            Object this$dstRack = this.getDstRack();
                            Object other$dstRack = other.getDstRack();
                            return this$dstRack == null ? other$dstRack == null : this$dstRack.equals(other$dstRack);
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
            return other instanceof WorkorderController.AssetLineReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $dstUnit = this.getDstUnit();
            result = result * 59 + ($dstUnit == null ? 43 : $dstUnit.hashCode());
            Object $assetNo = this.getAssetNo();
            result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
            Object $dstRoom = this.getDstRoom();
            result = result * 59 + ($dstRoom == null ? 43 : $dstRoom.hashCode());
            Object $dstRack = this.getDstRack();
            return result * 59 + ($dstRack == null ? 43 : $dstRack.hashCode());
        }

        
        @Override
        public String toString() {
            return "WorkorderController.AssetLineReq(assetNo="
                + this.getAssetNo()
                + ", dstRoom="
                + this.getDstRoom()
                + ", dstRack="
                + this.getDstRack()
                + ", dstUnit="
                + this.getDstUnit()
                + ")";
        }
    }

    public static class CommentReq {
        @NotBlank(
            message = "评论内容不能为空"
        )
        private String content;

        
        public String getContent() {
            return this.content;
        }

        
        public void setContent(final String content) {
            this.content = content;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WorkorderController.CommentReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$content = this.getContent();
                Object other$content = other.getContent();
                return this$content == null ? other$content == null : this$content.equals(other$content);
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof WorkorderController.CommentReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $content = this.getContent();
            return result * 59 + ($content == null ? 43 : $content.hashCode());
        }

        
        @Override
        public String toString() {
            return "WorkorderController.CommentReq(content=" + this.getContent() + ")";
        }
    }

    public static class CreateWoReq {
        @NotBlank(
            message = "工单类型不能为空"
        )
        private String woType;
        @NotBlank(
            message = "工单标题不能为空"
        )
        private String title;
        private String description;
        private String priority;
        /**
         * 关联资产编号；兼容第三方 / 测试脚本常用的 assetNos、assetNoList 等字段名（v3.2 中这些字段被静默丢弃）。
         * v3.3.1：取值也兼容单个字符串、逗号分隔字符串、对象数组，见 {@link WoAssetJson}。
         */
        @com.fasterxml.jackson.annotation.JsonAlias({"assetNos", "assetNoList", "assetList", "assetNo", "relatedAssetNos", "asset_nos", "related_assets"})
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = WoAssetJson.AssetNoList.class)
        private List<String> relatedAssets;
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = WoAssetJson.AssetLines.class)
        private List<WorkorderController.AssetLineReq> assets;
        /** 未识别字段（只用于拒绝认不出的资产字段，不参与业务） */
        @com.fasterxml.jackson.annotation.JsonIgnore
        private final Map<String, Object> unknownFields = new LinkedHashMap<>();

        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void putUnknownField(String key, Object value) {
            this.unknownFields.put(key, value);
        }

        public Map<String, Object> unknownFields() {
            return this.unknownFields;
        }
        private List<String> relatedMaterials;
        private Integer applyQty;
        private String targetLocation;
        private String targetRack;
        private Integer targetUnit;
        private String expectFinish;
        private List<Map<String, Object>> details;
        private List<WorkorderController.ReplacementReq> replacements;
        private String parentWo;
        private String replacedAsset;
        private List<String> approvalApprovers;

        
        public String getWoType() {
            return this.woType;
        }

        
        public String getTitle() {
            return this.title;
        }

        
        public String getDescription() {
            return this.description;
        }

        
        public String getPriority() {
            return this.priority;
        }

        
        public List<String> getRelatedAssets() {
            return this.relatedAssets;
        }

        
        public List<WorkorderController.AssetLineReq> getAssets() {
            return this.assets;
        }

        
        public List<String> getRelatedMaterials() {
            return this.relatedMaterials;
        }

        
        public Integer getApplyQty() {
            return this.applyQty;
        }

        
        public String getTargetLocation() {
            return this.targetLocation;
        }

        
        public String getTargetRack() {
            return this.targetRack;
        }

        
        public Integer getTargetUnit() {
            return this.targetUnit;
        }

        
        public String getExpectFinish() {
            return this.expectFinish;
        }

        
        public List<Map<String, Object>> getDetails() {
            return this.details;
        }

        
        public List<WorkorderController.ReplacementReq> getReplacements() {
            return this.replacements;
        }

        
        public String getParentWo() {
            return this.parentWo;
        }

        
        public String getReplacedAsset() {
            return this.replacedAsset;
        }

        
        public List<String> getApprovalApprovers() {
            return this.approvalApprovers;
        }

        
        public void setWoType(final String woType) {
            this.woType = woType;
        }

        
        public void setTitle(final String title) {
            this.title = title;
        }

        
        public void setDescription(final String description) {
            this.description = description;
        }

        
        public void setPriority(final String priority) {
            this.priority = priority;
        }

        
        public void setRelatedAssets(final List<String> relatedAssets) {
            this.relatedAssets = relatedAssets;
        }

        
        public void setAssets(final List<WorkorderController.AssetLineReq> assets) {
            this.assets = assets;
        }

        
        public void setRelatedMaterials(final List<String> relatedMaterials) {
            this.relatedMaterials = relatedMaterials;
        }

        
        public void setApplyQty(final Integer applyQty) {
            this.applyQty = applyQty;
        }

        
        public void setTargetLocation(final String targetLocation) {
            this.targetLocation = targetLocation;
        }

        
        public void setTargetRack(final String targetRack) {
            this.targetRack = targetRack;
        }

        
        public void setTargetUnit(final Integer targetUnit) {
            this.targetUnit = targetUnit;
        }

        
        public void setExpectFinish(final String expectFinish) {
            this.expectFinish = expectFinish;
        }

        
        public void setDetails(final List<Map<String, Object>> details) {
            this.details = details;
        }

        
        public void setReplacements(final List<WorkorderController.ReplacementReq> replacements) {
            this.replacements = replacements;
        }

        
        public void setParentWo(final String parentWo) {
            this.parentWo = parentWo;
        }

        
        public void setReplacedAsset(final String replacedAsset) {
            this.replacedAsset = replacedAsset;
        }

        
        public void setApprovalApprovers(final List<String> approvalApprovers) {
            this.approvalApprovers = approvalApprovers;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WorkorderController.CreateWoReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$applyQty = this.getApplyQty();
                Object other$applyQty = other.getApplyQty();
                if (this$applyQty == null ? other$applyQty == null : this$applyQty.equals(other$applyQty)) {
                    Object this$targetUnit = this.getTargetUnit();
                    Object other$targetUnit = other.getTargetUnit();
                    if (this$targetUnit == null ? other$targetUnit == null : this$targetUnit.equals(other$targetUnit)) {
                        Object this$woType = this.getWoType();
                        Object other$woType = other.getWoType();
                        if (this$woType == null ? other$woType == null : this$woType.equals(other$woType)) {
                            Object this$title = this.getTitle();
                            Object other$title = other.getTitle();
                            if (this$title == null ? other$title == null : this$title.equals(other$title)) {
                                Object this$description = this.getDescription();
                                Object other$description = other.getDescription();
                                if (this$description == null ? other$description == null : this$description.equals(other$description)) {
                                    Object this$priority = this.getPriority();
                                    Object other$priority = other.getPriority();
                                    if (this$priority == null ? other$priority == null : this$priority.equals(other$priority)) {
                                        Object this$relatedAssets = this.getRelatedAssets();
                                        Object other$relatedAssets = other.getRelatedAssets();
                                        if (this$relatedAssets == null ? other$relatedAssets == null : this$relatedAssets.equals(other$relatedAssets)) {
                                            Object this$assets = this.getAssets();
                                            Object other$assets = other.getAssets();
                                            if (this$assets == null ? other$assets == null : this$assets.equals(other$assets)) {
                                                Object this$relatedMaterials = this.getRelatedMaterials();
                                                Object other$relatedMaterials = other.getRelatedMaterials();
                                                if (this$relatedMaterials == null
                                                    ? other$relatedMaterials == null
                                                    : this$relatedMaterials.equals(other$relatedMaterials)) {
                                                    Object this$targetLocation = this.getTargetLocation();
                                                    Object other$targetLocation = other.getTargetLocation();
                                                    if (this$targetLocation == null
                                                        ? other$targetLocation == null
                                                        : this$targetLocation.equals(other$targetLocation)) {
                                                        Object this$targetRack = this.getTargetRack();
                                                        Object other$targetRack = other.getTargetRack();
                                                        if (this$targetRack == null ? other$targetRack == null : this$targetRack.equals(other$targetRack)) {
                                                            Object this$expectFinish = this.getExpectFinish();
                                                            Object other$expectFinish = other.getExpectFinish();
                                                            if (this$expectFinish == null
                                                                ? other$expectFinish == null
                                                                : this$expectFinish.equals(other$expectFinish)) {
                                                                Object this$details = this.getDetails();
                                                                Object other$details = other.getDetails();
                                                                if (this$details == null ? other$details == null : this$details.equals(other$details)) {
                                                                    Object this$replacements = this.getReplacements();
                                                                    Object other$replacements = other.getReplacements();
                                                                    if (this$replacements == null
                                                                        ? other$replacements == null
                                                                        : this$replacements.equals(other$replacements)) {
                                                                        Object this$parentWo = this.getParentWo();
                                                                        Object other$parentWo = other.getParentWo();
                                                                        if (this$parentWo == null
                                                                            ? other$parentWo == null
                                                                            : this$parentWo.equals(other$parentWo)) {
                                                                            Object this$replacedAsset = this.getReplacedAsset();
                                                                            Object other$replacedAsset = other.getReplacedAsset();
                                                                            if (this$replacedAsset == null
                                                                                ? other$replacedAsset == null
                                                                                : this$replacedAsset.equals(other$replacedAsset)) {
                                                                                Object this$approvalApprovers = this.getApprovalApprovers();
                                                                                Object other$approvalApprovers = other.getApprovalApprovers();
                                                                                return this$approvalApprovers == null
                                                                                    ? other$approvalApprovers == null
                                                                                    : this$approvalApprovers.equals(other$approvalApprovers);
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
            return other instanceof WorkorderController.CreateWoReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $applyQty = this.getApplyQty();
            result = result * 59 + ($applyQty == null ? 43 : $applyQty.hashCode());
            Object $targetUnit = this.getTargetUnit();
            result = result * 59 + ($targetUnit == null ? 43 : $targetUnit.hashCode());
            Object $woType = this.getWoType();
            result = result * 59 + ($woType == null ? 43 : $woType.hashCode());
            Object $title = this.getTitle();
            result = result * 59 + ($title == null ? 43 : $title.hashCode());
            Object $description = this.getDescription();
            result = result * 59 + ($description == null ? 43 : $description.hashCode());
            Object $priority = this.getPriority();
            result = result * 59 + ($priority == null ? 43 : $priority.hashCode());
            Object $relatedAssets = this.getRelatedAssets();
            result = result * 59 + ($relatedAssets == null ? 43 : $relatedAssets.hashCode());
            Object $assets = this.getAssets();
            result = result * 59 + ($assets == null ? 43 : $assets.hashCode());
            Object $relatedMaterials = this.getRelatedMaterials();
            result = result * 59 + ($relatedMaterials == null ? 43 : $relatedMaterials.hashCode());
            Object $targetLocation = this.getTargetLocation();
            result = result * 59 + ($targetLocation == null ? 43 : $targetLocation.hashCode());
            Object $targetRack = this.getTargetRack();
            result = result * 59 + ($targetRack == null ? 43 : $targetRack.hashCode());
            Object $expectFinish = this.getExpectFinish();
            result = result * 59 + ($expectFinish == null ? 43 : $expectFinish.hashCode());
            Object $details = this.getDetails();
            result = result * 59 + ($details == null ? 43 : $details.hashCode());
            Object $replacements = this.getReplacements();
            result = result * 59 + ($replacements == null ? 43 : $replacements.hashCode());
            Object $parentWo = this.getParentWo();
            result = result * 59 + ($parentWo == null ? 43 : $parentWo.hashCode());
            Object $replacedAsset = this.getReplacedAsset();
            result = result * 59 + ($replacedAsset == null ? 43 : $replacedAsset.hashCode());
            Object $approvalApprovers = this.getApprovalApprovers();
            return result * 59 + ($approvalApprovers == null ? 43 : $approvalApprovers.hashCode());
        }

        
        @Override
        public String toString() {
            return "WorkorderController.CreateWoReq(woType="
                + this.getWoType()
                + ", title="
                + this.getTitle()
                + ", description="
                + this.getDescription()
                + ", priority="
                + this.getPriority()
                + ", relatedAssets="
                + this.getRelatedAssets()
                + ", assets="
                + this.getAssets()
                + ", relatedMaterials="
                + this.getRelatedMaterials()
                + ", applyQty="
                + this.getApplyQty()
                + ", targetLocation="
                + this.getTargetLocation()
                + ", targetRack="
                + this.getTargetRack()
                + ", targetUnit="
                + this.getTargetUnit()
                + ", expectFinish="
                + this.getExpectFinish()
                + ", details="
                + this.getDetails()
                + ", replacements="
                + this.getReplacements()
                + ", parentWo="
                + this.getParentWo()
                + ", replacedAsset="
                + this.getReplacedAsset()
                + ", approvalApprovers="
                + this.getApprovalApprovers()
                + ")";
        }
    }

    public static class ItemReq {
        @NotBlank(
            message = "资产编号不能为空"
        )
        private String assetNo;
        private String dstRoom;
        private String dstRack;
        private Integer dstUnit;

        
        public String getAssetNo() {
            return this.assetNo;
        }

        
        public String getDstRoom() {
            return this.dstRoom;
        }

        
        public String getDstRack() {
            return this.dstRack;
        }

        
        public Integer getDstUnit() {
            return this.dstUnit;
        }

        
        public void setAssetNo(final String assetNo) {
            this.assetNo = assetNo;
        }

        
        public void setDstRoom(final String dstRoom) {
            this.dstRoom = dstRoom;
        }

        
        public void setDstRack(final String dstRack) {
            this.dstRack = dstRack;
        }

        
        public void setDstUnit(final Integer dstUnit) {
            this.dstUnit = dstUnit;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WorkorderController.ItemReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$dstUnit = this.getDstUnit();
                Object other$dstUnit = other.getDstUnit();
                if (this$dstUnit == null ? other$dstUnit == null : this$dstUnit.equals(other$dstUnit)) {
                    Object this$assetNo = this.getAssetNo();
                    Object other$assetNo = other.getAssetNo();
                    if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                        Object this$dstRoom = this.getDstRoom();
                        Object other$dstRoom = other.getDstRoom();
                        if (this$dstRoom == null ? other$dstRoom == null : this$dstRoom.equals(other$dstRoom)) {
                            Object this$dstRack = this.getDstRack();
                            Object other$dstRack = other.getDstRack();
                            return this$dstRack == null ? other$dstRack == null : this$dstRack.equals(other$dstRack);
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
            return other instanceof WorkorderController.ItemReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $dstUnit = this.getDstUnit();
            result = result * 59 + ($dstUnit == null ? 43 : $dstUnit.hashCode());
            Object $assetNo = this.getAssetNo();
            result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
            Object $dstRoom = this.getDstRoom();
            result = result * 59 + ($dstRoom == null ? 43 : $dstRoom.hashCode());
            Object $dstRack = this.getDstRack();
            return result * 59 + ($dstRack == null ? 43 : $dstRack.hashCode());
        }

        
        @Override
        public String toString() {
            return "WorkorderController.ItemReq(assetNo="
                + this.getAssetNo()
                + ", dstRoom="
                + this.getDstRoom()
                + ", dstRack="
                + this.getDstRack()
                + ", dstUnit="
                + this.getDstUnit()
                + ")";
        }
    }

    public static class RemindReq {
        private String opinion;

        
        public String getOpinion() {
            return this.opinion;
        }

        
        public void setOpinion(final String opinion) {
            this.opinion = opinion;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WorkorderController.RemindReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$opinion = this.getOpinion();
                Object other$opinion = other.getOpinion();
                return this$opinion == null ? other$opinion == null : this$opinion.equals(other$opinion);
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof WorkorderController.RemindReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $opinion = this.getOpinion();
            return result * 59 + ($opinion == null ? 43 : $opinion.hashCode());
        }

        
        @Override
        public String toString() {
            return "WorkorderController.RemindReq(opinion=" + this.getOpinion() + ")";
        }
    }

    public static class RepairCostReq {
        @NotNull(
            message = "维修人工费不能为空（无则传 0）"
        )
        private BigDecimal costLabor;
        @NotNull(
            message = "外委服务费不能为空（无则传 0）"
        )
        private BigDecimal costService;
        @NotNull(
            message = "其他费用不能为空（无则传 0）"
        )
        private BigDecimal costOther;

        
        public BigDecimal getCostLabor() {
            return this.costLabor;
        }

        
        public BigDecimal getCostService() {
            return this.costService;
        }

        
        public BigDecimal getCostOther() {
            return this.costOther;
        }

        
        public void setCostLabor(final BigDecimal costLabor) {
            this.costLabor = costLabor;
        }

        
        public void setCostService(final BigDecimal costService) {
            this.costService = costService;
        }

        
        public void setCostOther(final BigDecimal costOther) {
            this.costOther = costOther;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WorkorderController.RepairCostReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$costLabor = this.getCostLabor();
                Object other$costLabor = other.getCostLabor();
                if (this$costLabor == null ? other$costLabor == null : this$costLabor.equals(other$costLabor)) {
                    Object this$costService = this.getCostService();
                    Object other$costService = other.getCostService();
                    if (this$costService == null ? other$costService == null : this$costService.equals(other$costService)) {
                        Object this$costOther = this.getCostOther();
                        Object other$costOther = other.getCostOther();
                        return this$costOther == null ? other$costOther == null : this$costOther.equals(other$costOther);
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof WorkorderController.RepairCostReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $costLabor = this.getCostLabor();
            result = result * 59 + ($costLabor == null ? 43 : $costLabor.hashCode());
            Object $costService = this.getCostService();
            result = result * 59 + ($costService == null ? 43 : $costService.hashCode());
            Object $costOther = this.getCostOther();
            return result * 59 + ($costOther == null ? 43 : $costOther.hashCode());
        }

        
        @Override
        public String toString() {
            return "WorkorderController.RepairCostReq(costLabor="
                + this.getCostLabor()
                + ", costService="
                + this.getCostService()
                + ", costOther="
                + this.getCostOther()
                + ")";
        }
    }

    public static class ReplacementReq {
        @NotBlank(
            message = "父资产编号不能为空"
        )
        private String parentAssetNo;
        @NotBlank(
            message = "旧件资产编号不能为空"
        )
        private String oldAssetNo;
        @NotBlank(
            message = "新件资产编号不能为空"
        )
        private String newAssetNo;
        private String oldStatus;

        
        public String getParentAssetNo() {
            return this.parentAssetNo;
        }

        
        public String getOldAssetNo() {
            return this.oldAssetNo;
        }

        
        public String getNewAssetNo() {
            return this.newAssetNo;
        }

        
        public String getOldStatus() {
            return this.oldStatus;
        }

        
        public void setParentAssetNo(final String parentAssetNo) {
            this.parentAssetNo = parentAssetNo;
        }

        
        public void setOldAssetNo(final String oldAssetNo) {
            this.oldAssetNo = oldAssetNo;
        }

        
        public void setNewAssetNo(final String newAssetNo) {
            this.newAssetNo = newAssetNo;
        }

        
        public void setOldStatus(final String oldStatus) {
            this.oldStatus = oldStatus;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WorkorderController.ReplacementReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$parentAssetNo = this.getParentAssetNo();
                Object other$parentAssetNo = other.getParentAssetNo();
                if (this$parentAssetNo == null ? other$parentAssetNo == null : this$parentAssetNo.equals(other$parentAssetNo)) {
                    Object this$oldAssetNo = this.getOldAssetNo();
                    Object other$oldAssetNo = other.getOldAssetNo();
                    if (this$oldAssetNo == null ? other$oldAssetNo == null : this$oldAssetNo.equals(other$oldAssetNo)) {
                        Object this$newAssetNo = this.getNewAssetNo();
                        Object other$newAssetNo = other.getNewAssetNo();
                        if (this$newAssetNo == null ? other$newAssetNo == null : this$newAssetNo.equals(other$newAssetNo)) {
                            Object this$oldStatus = this.getOldStatus();
                            Object other$oldStatus = other.getOldStatus();
                            return this$oldStatus == null ? other$oldStatus == null : this$oldStatus.equals(other$oldStatus);
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
            return other instanceof WorkorderController.ReplacementReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $parentAssetNo = this.getParentAssetNo();
            result = result * 59 + ($parentAssetNo == null ? 43 : $parentAssetNo.hashCode());
            Object $oldAssetNo = this.getOldAssetNo();
            result = result * 59 + ($oldAssetNo == null ? 43 : $oldAssetNo.hashCode());
            Object $newAssetNo = this.getNewAssetNo();
            result = result * 59 + ($newAssetNo == null ? 43 : $newAssetNo.hashCode());
            Object $oldStatus = this.getOldStatus();
            return result * 59 + ($oldStatus == null ? 43 : $oldStatus.hashCode());
        }

        
        @Override
        public String toString() {
            return "WorkorderController.ReplacementReq(parentAssetNo="
                + this.getParentAssetNo()
                + ", oldAssetNo="
                + this.getOldAssetNo()
                + ", newAssetNo="
                + this.getNewAssetNo()
                + ", oldStatus="
                + this.getOldStatus()
                + ")";
        }
    }

    public static class ReplacementsReq {
        private List<WorkorderController.ReplacementReq> replacements;

        
        public List<WorkorderController.ReplacementReq> getReplacements() {
            return this.replacements;
        }

        
        public void setReplacements(final List<WorkorderController.ReplacementReq> replacements) {
            this.replacements = replacements;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WorkorderController.ReplacementsReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$replacements = this.getReplacements();
                Object other$replacements = other.getReplacements();
                return this$replacements == null ? other$replacements == null : this$replacements.equals(other$replacements);
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof WorkorderController.ReplacementsReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $replacements = this.getReplacements();
            return result * 59 + ($replacements == null ? 43 : $replacements.hashCode());
        }

        
        @Override
        public String toString() {
            return "WorkorderController.ReplacementsReq(replacements=" + this.getReplacements() + ")";
        }
    }

    public static class SlaConfigUpdateReq {
        private Integer responseMinutes;
        private Integer resolveMinutes;
        private String escalateTo;

        
        public Integer getResponseMinutes() {
            return this.responseMinutes;
        }

        
        public Integer getResolveMinutes() {
            return this.resolveMinutes;
        }

        
        public String getEscalateTo() {
            return this.escalateTo;
        }

        
        public void setResponseMinutes(final Integer responseMinutes) {
            this.responseMinutes = responseMinutes;
        }

        
        public void setResolveMinutes(final Integer resolveMinutes) {
            this.resolveMinutes = resolveMinutes;
        }

        
        public void setEscalateTo(final String escalateTo) {
            this.escalateTo = escalateTo;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WorkorderController.SlaConfigUpdateReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$responseMinutes = this.getResponseMinutes();
                Object other$responseMinutes = other.getResponseMinutes();
                if (this$responseMinutes == null ? other$responseMinutes == null : this$responseMinutes.equals(other$responseMinutes)) {
                    Object this$resolveMinutes = this.getResolveMinutes();
                    Object other$resolveMinutes = other.getResolveMinutes();
                    if (this$resolveMinutes == null ? other$resolveMinutes == null : this$resolveMinutes.equals(other$resolveMinutes)) {
                        Object this$escalateTo = this.getEscalateTo();
                        Object other$escalateTo = other.getEscalateTo();
                        return this$escalateTo == null ? other$escalateTo == null : this$escalateTo.equals(other$escalateTo);
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof WorkorderController.SlaConfigUpdateReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $responseMinutes = this.getResponseMinutes();
            result = result * 59 + ($responseMinutes == null ? 43 : $responseMinutes.hashCode());
            Object $resolveMinutes = this.getResolveMinutes();
            result = result * 59 + ($resolveMinutes == null ? 43 : $resolveMinutes.hashCode());
            Object $escalateTo = this.getEscalateTo();
            return result * 59 + ($escalateTo == null ? 43 : $escalateTo.hashCode());
        }

        
        @Override
        public String toString() {
            return "WorkorderController.SlaConfigUpdateReq(responseMinutes="
                + this.getResponseMinutes()
                + ", resolveMinutes="
                + this.getResolveMinutes()
                + ", escalateTo="
                + this.getEscalateTo()
                + ")";
        }
    }

    public static class TransitionReq {
        @NotBlank(
            message = "流转动作不能为空"
        )
        private String action;
        private String opinion;
        private String rootCause;
        private String replacedAsset;
        private String completeNote;
        private BigDecimal costLabor;
        private BigDecimal costService;
        private BigDecimal costOther;

        
        public String getAction() {
            return this.action;
        }

        
        public String getOpinion() {
            return this.opinion;
        }

        
        public String getRootCause() {
            return this.rootCause;
        }

        
        public String getReplacedAsset() {
            return this.replacedAsset;
        }

        
        public String getCompleteNote() {
            return this.completeNote;
        }

        
        public BigDecimal getCostLabor() {
            return this.costLabor;
        }

        
        public BigDecimal getCostService() {
            return this.costService;
        }

        
        public BigDecimal getCostOther() {
            return this.costOther;
        }

        
        public void setAction(final String action) {
            this.action = action;
        }

        
        public void setOpinion(final String opinion) {
            this.opinion = opinion;
        }

        
        public void setRootCause(final String rootCause) {
            this.rootCause = rootCause;
        }

        
        public void setReplacedAsset(final String replacedAsset) {
            this.replacedAsset = replacedAsset;
        }

        
        public void setCompleteNote(final String completeNote) {
            this.completeNote = completeNote;
        }

        
        public void setCostLabor(final BigDecimal costLabor) {
            this.costLabor = costLabor;
        }

        
        public void setCostService(final BigDecimal costService) {
            this.costService = costService;
        }

        
        public void setCostOther(final BigDecimal costOther) {
            this.costOther = costOther;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WorkorderController.TransitionReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$action = this.getAction();
                Object other$action = other.getAction();
                if (this$action == null ? other$action == null : this$action.equals(other$action)) {
                    Object this$opinion = this.getOpinion();
                    Object other$opinion = other.getOpinion();
                    if (this$opinion == null ? other$opinion == null : this$opinion.equals(other$opinion)) {
                        Object this$rootCause = this.getRootCause();
                        Object other$rootCause = other.getRootCause();
                        if (this$rootCause == null ? other$rootCause == null : this$rootCause.equals(other$rootCause)) {
                            Object this$replacedAsset = this.getReplacedAsset();
                            Object other$replacedAsset = other.getReplacedAsset();
                            if (this$replacedAsset == null ? other$replacedAsset == null : this$replacedAsset.equals(other$replacedAsset)) {
                                Object this$completeNote = this.getCompleteNote();
                                Object other$completeNote = other.getCompleteNote();
                                if (this$completeNote == null ? other$completeNote == null : this$completeNote.equals(other$completeNote)) {
                                    Object this$costLabor = this.getCostLabor();
                                    Object other$costLabor = other.getCostLabor();
                                    if (this$costLabor == null ? other$costLabor == null : this$costLabor.equals(other$costLabor)) {
                                        Object this$costService = this.getCostService();
                                        Object other$costService = other.getCostService();
                                        if (this$costService == null ? other$costService == null : this$costService.equals(other$costService)) {
                                            Object this$costOther = this.getCostOther();
                                            Object other$costOther = other.getCostOther();
                                            return this$costOther == null ? other$costOther == null : this$costOther.equals(other$costOther);
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
            return other instanceof WorkorderController.TransitionReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $action = this.getAction();
            result = result * 59 + ($action == null ? 43 : $action.hashCode());
            Object $opinion = this.getOpinion();
            result = result * 59 + ($opinion == null ? 43 : $opinion.hashCode());
            Object $rootCause = this.getRootCause();
            result = result * 59 + ($rootCause == null ? 43 : $rootCause.hashCode());
            Object $replacedAsset = this.getReplacedAsset();
            result = result * 59 + ($replacedAsset == null ? 43 : $replacedAsset.hashCode());
            Object $completeNote = this.getCompleteNote();
            result = result * 59 + ($completeNote == null ? 43 : $completeNote.hashCode());
            Object $costLabor = this.getCostLabor();
            result = result * 59 + ($costLabor == null ? 43 : $costLabor.hashCode());
            Object $costService = this.getCostService();
            result = result * 59 + ($costService == null ? 43 : $costService.hashCode());
            Object $costOther = this.getCostOther();
            return result * 59 + ($costOther == null ? 43 : $costOther.hashCode());
        }

        
        @Override
        public String toString() {
            return "WorkorderController.TransitionReq(action="
                + this.getAction()
                + ", opinion="
                + this.getOpinion()
                + ", rootCause="
                + this.getRootCause()
                + ", replacedAsset="
                + this.getReplacedAsset()
                + ", completeNote="
                + this.getCompleteNote()
                + ", costLabor="
                + this.getCostLabor()
                + ", costService="
                + this.getCostService()
                + ", costOther="
                + this.getCostOther()
                + ")";
        }
    }

    public static class UpdateWoReq {
        @NotBlank(
            message = "工单标题不能为空"
        )
        private String title;
        private String description;
        private String priority;
        private String expectFinish;
        /**
         * v3.3.1：可选。传入时把工单关联资产整体替换为该列表（新增的建行、移除的删行，与 /items 接口同一套校验）；
         * 不传则不改动关联资产。v3.3.0 及以前该字段被静默忽略。
         */
        @com.fasterxml.jackson.annotation.JsonAlias({"assetNos", "assetNoList", "assetList", "assetNo", "relatedAssetNos", "asset_nos", "related_assets"})
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = WoAssetJson.AssetNoList.class)
        private List<String> relatedAssets;
        @com.fasterxml.jackson.annotation.JsonIgnore
        private final Map<String, Object> unknownFields = new LinkedHashMap<>();

        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void putUnknownField(String key, Object value) {
            this.unknownFields.put(key, value);
        }

        public Map<String, Object> unknownFields() {
            return this.unknownFields;
        }

        public List<String> getRelatedAssets() {
            return this.relatedAssets;
        }

        public void setRelatedAssets(final List<String> relatedAssets) {
            this.relatedAssets = relatedAssets;
        }

        
        public String getTitle() {
            return this.title;
        }

        
        public String getDescription() {
            return this.description;
        }

        
        public String getPriority() {
            return this.priority;
        }

        
        public String getExpectFinish() {
            return this.expectFinish;
        }

        
        public void setTitle(final String title) {
            this.title = title;
        }

        
        public void setDescription(final String description) {
            this.description = description;
        }

        
        public void setPriority(final String priority) {
            this.priority = priority;
        }

        
        public void setExpectFinish(final String expectFinish) {
            this.expectFinish = expectFinish;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WorkorderController.UpdateWoReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$title = this.getTitle();
                Object other$title = other.getTitle();
                if (this$title == null ? other$title == null : this$title.equals(other$title)) {
                    Object this$description = this.getDescription();
                    Object other$description = other.getDescription();
                    if (this$description == null ? other$description == null : this$description.equals(other$description)) {
                        Object this$priority = this.getPriority();
                        Object other$priority = other.getPriority();
                        if (this$priority == null ? other$priority == null : this$priority.equals(other$priority)) {
                            Object this$expectFinish = this.getExpectFinish();
                            Object other$expectFinish = other.getExpectFinish();
                            return this$expectFinish == null ? other$expectFinish == null : this$expectFinish.equals(other$expectFinish);
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
            return other instanceof WorkorderController.UpdateWoReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $title = this.getTitle();
            result = result * 59 + ($title == null ? 43 : $title.hashCode());
            Object $description = this.getDescription();
            result = result * 59 + ($description == null ? 43 : $description.hashCode());
            Object $priority = this.getPriority();
            result = result * 59 + ($priority == null ? 43 : $priority.hashCode());
            Object $expectFinish = this.getExpectFinish();
            return result * 59 + ($expectFinish == null ? 43 : $expectFinish.hashCode());
        }

        
        @Override
        public String toString() {
            return "WorkorderController.UpdateWoReq(title="
                + this.getTitle()
                + ", description="
                + this.getDescription()
                + ", priority="
                + this.getPriority()
                + ", expectFinish="
                + this.getExpectFinish()
                + ")";
        }
    }

    public static class WorkflowConfigUpdateReq {
        private boolean needApproval;
        private List<String> approvalRoles;
        private List<List<String>> approvalUserLevels;

        
        public boolean isNeedApproval() {
            return this.needApproval;
        }

        
        public List<String> getApprovalRoles() {
            return this.approvalRoles;
        }

        
        public List<List<String>> getApprovalUserLevels() {
            return this.approvalUserLevels;
        }

        
        public void setNeedApproval(final boolean needApproval) {
            this.needApproval = needApproval;
        }

        
        public void setApprovalRoles(final List<String> approvalRoles) {
            this.approvalRoles = approvalRoles;
        }

        
        public void setApprovalUserLevels(final List<List<String>> approvalUserLevels) {
            this.approvalUserLevels = approvalUserLevels;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WorkorderController.WorkflowConfigUpdateReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else if (this.isNeedApproval() != other.isNeedApproval()) {
                return false;
            } else {
                Object this$approvalRoles = this.getApprovalRoles();
                Object other$approvalRoles = other.getApprovalRoles();
                if (this$approvalRoles == null ? other$approvalRoles == null : this$approvalRoles.equals(other$approvalRoles)) {
                    Object this$approvalUserLevels = this.getApprovalUserLevels();
                    Object other$approvalUserLevels = other.getApprovalUserLevels();
                    return this$approvalUserLevels == null ? other$approvalUserLevels == null : this$approvalUserLevels.equals(other$approvalUserLevels);
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof WorkorderController.WorkflowConfigUpdateReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            result = result * 59 + (this.isNeedApproval() ? 79 : 97);
            Object $approvalRoles = this.getApprovalRoles();
            result = result * 59 + ($approvalRoles == null ? 43 : $approvalRoles.hashCode());
            Object $approvalUserLevels = this.getApprovalUserLevels();
            return result * 59 + ($approvalUserLevels == null ? 43 : $approvalUserLevels.hashCode());
        }

        
        @Override
        public String toString() {
            return "WorkorderController.WorkflowConfigUpdateReq(needApproval="
                + this.isNeedApproval()
                + ", approvalRoles="
                + this.getApprovalRoles()
                + ", approvalUserLevels="
                + this.getApprovalUserLevels()
                + ")";
        }
    }
}
