package com.dcim.change.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.change.entity.ChangeRequest;
import com.dcim.change.mapper.ChangeRequestMapper;
import com.dcim.change.service.ChangeService;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
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

@RestController
@RequestMapping({"/api/changes"})
public class ChangeController {
    private final ChangeRequestMapper crMapper;
    private final ChangeService changeService;

    @GetMapping
    @RequirePerm(
        module = "changes"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean mine
    ) {
        LambdaQueryWrapper<ChangeRequest> q = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) {
            q.eq(ChangeRequest::getStatus, status.trim());
        }

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> ((w.like(ChangeRequest::getCrNo, kw)).or()).like(ChangeRequest::getTitle, kw));
        }

        if (Boolean.TRUE.equals(mine)) {
            q.eq(ChangeRequest::getRequester, requireUser().username());
        }

        q.orderByDesc(ChangeRequest::getId);
        Page<ChangeRequest> p = (Page<ChangeRequest>)this.crMapper.selectPage(Page.of(page, Math.min(size, 200L)), q);
        return ApiResult.ok(new PageResult<>(p.getTotal(), p.getRecords().stream().map(this::toRow).toList()));
    }

    @GetMapping({"/{id}"})
    @RequirePerm(
        module = "changes"
    )
    public ApiResult<Map<String, Object>> detail(@PathVariable Long id) {
        ChangeRequest cr = this.changeService.require(id);
        List<String> assets = this.changeService.assetsOf(id);
        Map<String, Object> out = this.toRow(cr);
        out.put("assetNos", assets);
        out.put("conflicts", this.changeService.conflicts(cr));
        out.put("impact", this.changeService.impact(assets));
        return ApiResult.ok(out);
    }

    @PostMapping
    @RequirePerm(
        module = "changes",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "changes",
        objType = "change",
        objIdSpEL = "#req.title()"
    )
    public ApiResult<Map<String, Object>> create(@RequestBody @Valid ChangeController.DraftReq req) {
        return ApiResult.ok(this.toRow(this.changeService.create(req.toDraft(), requireUser())));
    }

    @PutMapping({"/{id}"})
    @RequirePerm(
        module = "changes",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "changes",
        objType = "change",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> update(@PathVariable Long id, @RequestBody @Valid ChangeController.DraftReq req) {
        return ApiResult.ok(this.toRow(this.changeService.update(id, req.toDraft(), requireUser())));
    }

    @PostMapping({"/{id}/submit"})
    @RequirePerm(
        module = "changes",
        write = true
    )
    @Audit(
        action = "SUBMIT",
        module = "changes",
        objType = "change",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> submit(@PathVariable Long id) {
        return ApiResult.ok(this.toRow(this.changeService.submit(id, requireUser())));
    }

    @PostMapping({"/{id}/cab-approve"})
    @RequirePerm(
        module = "changes",
        write = true
    )
    @Audit(
        action = "APPROVE",
        module = "changes",
        objType = "change",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> cabApprove(@PathVariable Long id, @RequestBody @Valid ChangeController.ReviewReq req) {
        if (req.approved() == null) {
            throw BusinessException.badRequest("请选择批准或驳回");
        } else {
            return ApiResult.ok(this.toRow(this.changeService.review(id, req.approved(), req.comment(), requireUser())));
        }
    }

    @PostMapping({"/{id}/start"})
    @RequirePerm(
        module = "changes",
        write = true
    )
    @Audit(
        action = "START",
        module = "changes",
        objType = "change",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> start(@PathVariable Long id) {
        return ApiResult.ok(this.toRow(this.changeService.start(id, requireUser())));
    }

    @PostMapping({"/{id}/complete"})
    @RequirePerm(
        module = "changes",
        write = true
    )
    @Audit(
        action = "COMPLETE",
        module = "changes",
        objType = "change",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> complete(@PathVariable Long id, @RequestBody(required = false) ChangeController.NoteReq req) {
        return ApiResult.ok(this.toRow(this.changeService.finish(id, true, req == null ? null : req.note(), requireUser())));
    }

    @PostMapping({"/{id}/fail"})
    @RequirePerm(
        module = "changes",
        write = true
    )
    @Audit(
        action = "FAIL",
        module = "changes",
        objType = "change",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> fail(@PathVariable Long id, @RequestBody ChangeController.NoteReq req) {
        return ApiResult.ok(this.toRow(this.changeService.finish(id, false, req == null ? null : req.note(), requireUser())));
    }

    @PostMapping({"/{id}/cancel"})
    @RequirePerm(
        module = "changes",
        write = true
    )
    @Audit(
        action = "CANCEL",
        module = "changes",
        objType = "change",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> cancel(@PathVariable Long id, @RequestBody(required = false) ChangeController.NoteReq req) {
        return ApiResult.ok(this.toRow(this.changeService.cancel(id, req == null ? null : req.note(), requireUser())));
    }

    @DeleteMapping({"/{id}"})
    @RequirePerm(
        module = "changes",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "changes",
        objType = "change",
        objIdSpEL = "#id"
    )
    public ApiResult<Void> delete(@PathVariable Long id) {
        this.changeService.delete(id, requireUser());
        return ApiResult.ok();
    }

    private static LoginUser requireUser() {
        LoginUser u = CurrentUser.get();
        if (u == null) {
            throw BusinessException.unauthorized("未登录或会话已过期");
        } else {
            return u;
        }
    }

    private Map<String, Object> toRow(ChangeRequest c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("crNo", c.getCrNo());
        m.put("title", c.getTitle());
        m.put("description", c.getDescription());
        m.put("changeType", c.getChangeType());
        m.put("riskLevel", c.getRiskLevel());
        m.put("impactScope", c.getImpactScope());
        m.put("rollbackPlan", c.getRollbackPlan());
        m.put("status", c.getStatus());
        m.put("statusLabel", ChangeService.label(c.getStatus()));
        m.put("requester", c.getRequester());
        m.put("cabApproval", c.getCabApproval());
        m.put("cabApprovers", c.getCabApprovers());
        m.put("cabComment", c.getCabComment());
        m.put("approvedBy", c.getApprovedBy());
        m.put("approvedAt", c.getApprovedAt());
        m.put("scheduledStart", c.getScheduledStart());
        m.put("scheduledEnd", c.getScheduledEnd());
        m.put("actualStart", c.getActualStart());
        m.put("actualEnd", c.getActualEnd());
        m.put("closeNote", c.getCloseNote());
        m.put("cancelReason", c.getCancelReason());
        m.put("createdAt", c.getCreatedAt());
        m.put("updatedAt", c.getUpdatedAt());
        return m;
    }

    
    public ChangeController(final ChangeRequestMapper crMapper, final ChangeService changeService) {
        this.crMapper = crMapper;
        this.changeService = changeService;
    }

    public static record DraftReq(
        @Size(max = 200) String title,
        String description,
        String changeType,
        String riskLevel,
        String impactScope,
        String rollbackPlan,
        LocalDateTime scheduledStart,
        LocalDateTime scheduledEnd,
        List<String> assetNos
    ) {
        ChangeService.Draft toDraft() {
            return new ChangeService.Draft(
                this.title,
                this.description,
                this.changeType,
                this.riskLevel,
                this.impactScope,
                this.rollbackPlan,
                this.scheduledStart,
                this.scheduledEnd,
                this.assetNos
            );
        }
    }

    public static record NoteReq(@Size(max = 2000) String note) {
    }

    public static record ReviewReq(Boolean approved, @Size(max = 1000) String comment) {
    }
}
