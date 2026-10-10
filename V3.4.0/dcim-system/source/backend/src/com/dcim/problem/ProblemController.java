package com.dcim.problem;

import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
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
@RequestMapping({"/api/problems"})
public class ProblemController {
    private final ProblemService problems;

    @GetMapping
    @RequirePerm(
        module = "problems"
    )
    public ApiResult<Map<String, Object>> list(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String owner,
        @RequestParam(required = false) String assetNo,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResult.ok(this.problems.list(status, keyword, owner, assetNo, page, size));
    }

    @GetMapping({"/stats"})
    @RequirePerm(
        module = "problems"
    )
    public ApiResult<Map<String, Object>> stats() {
        return ApiResult.ok(this.problems.stats());
    }

    @GetMapping({"/suggestions"})
    @RequirePerm(
        module = "problems"
    )
    public ApiResult<Map<String, Object>> suggestions(
        @RequestParam(defaultValue = "3") int faults, @RequestParam(defaultValue = "3") int alerts, @RequestParam(defaultValue = "3") int models
    ) {
        return ApiResult.ok(this.problems.suggestions(faults, alerts, models));
    }

    @GetMapping({"/by-ref"})
    @RequirePerm(
        module = "problems"
    )
    public ApiResult<List<Map<String, Object>>> byRef(@RequestParam String type, @RequestParam String ref) {
        return ApiResult.ok(this.problems.byRef(type, ref));
    }

    @GetMapping({"/{idOrNo}"})
    @RequirePerm(
        module = "problems"
    )
    public ApiResult<Map<String, Object>> detail(@PathVariable String idOrNo) {
        return ApiResult.ok(this.problems.detail(idOrNo));
    }

    @PostMapping
    @RequirePerm(
        module = "problems",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "problems",
        objType = "problem",
        objIdSpEL = "#req.title()"
    )
    public ApiResult<Map<String, Object>> create(@RequestBody ProblemService.ProblemReq req) {
        return ApiResult.ok(this.problems.create(req, user()));
    }

    @PutMapping({"/{idOrNo}"})
    @RequirePerm(
        module = "problems",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "problems",
        objType = "problem",
        objIdSpEL = "#idOrNo"
    )
    public ApiResult<Map<String, Object>> update(@PathVariable String idOrNo, @RequestBody ProblemService.ProblemReq req) {
        return ApiResult.ok(this.problems.update(idOrNo, req, user()));
    }

    @PostMapping({"/{idOrNo}/transition"})
    @RequirePerm(
        module = "problems",
        write = true,
        action = "update"
    )
    @Audit(
        action = "TRANSITION",
        module = "problems",
        objType = "problem",
        objIdSpEL = "#idOrNo"
    )
    public ApiResult<Map<String, Object>> transition(@PathVariable String idOrNo, @RequestBody ProblemService.TransitionReq req) {
        return ApiResult.ok(this.problems.transition(idOrNo, req, user()));
    }

    @PostMapping({"/{idOrNo}/links"})
    @RequirePerm(
        module = "problems",
        write = true,
        action = "update"
    )
    @Audit(
        action = "LINK",
        module = "problems",
        objType = "problem",
        objIdSpEL = "#idOrNo"
    )
    public ApiResult<Map<String, Object>> link(@PathVariable String idOrNo, @RequestBody ProblemService.LinkReq req) {
        return ApiResult.ok(this.problems.link(idOrNo, req, user()));
    }

    @DeleteMapping({"/{idOrNo}/links/{linkId}"})
    @RequirePerm(
        module = "problems",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UNLINK",
        module = "problems",
        objType = "problem",
        objIdSpEL = "#idOrNo"
    )
    public ApiResult<Void> unlink(@PathVariable String idOrNo, @PathVariable Long linkId) {
        this.problems.unlink(idOrNo, linkId);
        return ApiResult.ok();
    }

    @DeleteMapping({"/{idOrNo}"})
    @RequirePerm(
        module = "problems",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "problems",
        objType = "problem",
        objIdSpEL = "#idOrNo"
    )
    public ApiResult<Void> delete(@PathVariable String idOrNo) {
        this.problems.delete(idOrNo);
        return ApiResult.ok();
    }

    private static String user() {
        LoginUser u = CurrentUser.get();
        if (u == null) {
            throw BusinessException.unauthorized("未登录或会话已过期");
        } else {
            return u.username();
        }
    }

    
    public ProblemController(final ProblemService problems) {
        this.problems = problems;
    }
}
