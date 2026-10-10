package com.dcim.inspection.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.inspection.entity.InspectionPlan;
import com.dcim.inspection.mapper.InspectionPlanMapper;
import com.dcim.inspection.service.InspectionService;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
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
@RequestMapping({"/api/inspection-plans"})
public class InspectionPlanController {
    private final InspectionPlanMapper planMapper;
    private final InspectionService service;

    @GetMapping
    @RequirePerm(
        module = "workorders"
    )
    public ApiResult<PageResult<InspectionPlan>> list(
        @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size, @RequestParam(required = false) String keyword
    ) {
        LambdaQueryWrapper<InspectionPlan> q = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> ((w.like(InspectionPlan::getName, kw)).or()).like(InspectionPlan::getPlanNo, kw));
        }

        ((q.orderByDesc(InspectionPlan::getEnabled)).orderByAsc(InspectionPlan::getNextRunAt))
            .orderByDesc(InspectionPlan::getId);
        Page<InspectionPlan> p = (Page<InspectionPlan>)this.planMapper.selectPage(Page.of(page, Math.min(size, 200L)), q);
        return ApiResult.ok(PageResult.of(p));
    }

    @GetMapping({"/{id}"})
    @RequirePerm(
        module = "workorders"
    )
    public ApiResult<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResult.ok(Map.of("plan", this.service.require(id), "runs", this.service.runs(id, 20)));
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
        objType = "inspection_plan",
        objIdSpEL = "#req.name()"
    )
    public ApiResult<InspectionPlan> create(@RequestBody InspectionService.PlanReq req) {
        return ApiResult.ok(this.service.create(req, requireUser()));
    }

    @PutMapping({"/{id}"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "workorders",
        objType = "inspection_plan",
        objIdSpEL = "#id"
    )
    public ApiResult<InspectionPlan> update(@PathVariable Long id, @RequestBody InspectionService.PlanReq req) {
        return ApiResult.ok(this.service.update(id, req));
    }

    @DeleteMapping({"/{id}"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "workorders",
        objType = "inspection_plan",
        objIdSpEL = "#id"
    )
    public ApiResult<Void> delete(@PathVariable Long id) {
        this.service.require(id);
        this.planMapper.deleteById(id);
        return ApiResult.ok();
    }

    @PostMapping({"/{id}/run"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "create"
    )
    @Audit(
        action = "RUN",
        module = "workorders",
        objType = "inspection_plan",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> run(@PathVariable Long id) {
        return ApiResult.ok(Map.of("woNo", this.service.runNow(id)));
    }

    @GetMapping({"/{id}/runs"})
    @RequirePerm(
        module = "workorders"
    )
    public ApiResult<List<Map<String, Object>>> runs(@PathVariable Long id) {
        return ApiResult.ok(this.service.runs(id, 50));
    }

    private static LoginUser requireUser() {
        LoginUser u = CurrentUser.get();
        if (u == null) {
            throw BusinessException.unauthorized("未登录或会话已过期");
        } else {
            return u;
        }
    }

    
    public InspectionPlanController(final InspectionPlanMapper planMapper, final InspectionService service) {
        this.planMapper = planMapper;
        this.service = service;
    }
}
