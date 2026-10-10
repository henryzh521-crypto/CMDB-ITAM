package com.dcim.knowledge;

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
@RequestMapping({"/api/kb"})
public class KbController {
    private final KbService kb;

    @GetMapping({"/meta"})
    @RequirePerm(
        module = "knowledge"
    )
    public ApiResult<Map<String, Object>> meta() {
        return ApiResult.ok(Map.of("categories", KbService.CATEGORIES));
    }

    @GetMapping
    @RequirePerm(
        module = "knowledge"
    )
    public ApiResult<Map<String, Object>> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String tag,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResult.ok(this.kb.list(keyword, category, status, tag, canWrite(), page, size));
    }

    @GetMapping({"/recommend"})
    public ApiResult<List<Map<String, Object>>> recommend(
        @RequestParam(required = false) String alertname,
        @RequestParam(required = false) String assetNo,
        @RequestParam(required = false) String text,
        @RequestParam(defaultValue = "5") int limit
    ) {
        requireUser();
        return ApiResult.ok(this.kb.recommend(alertname, assetNo, text, limit));
    }

    @GetMapping({"/{idOrNo}"})
    public ApiResult<Map<String, Object>> get(@PathVariable String idOrNo) {
        requireUser();
        return ApiResult.ok(this.kb.get(idOrNo, canWrite(), true));
    }

    @PostMapping
    @RequirePerm(
        module = "knowledge",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "knowledge",
        objType = "kb_article",
        objIdSpEL = "#req.title()"
    )
    public ApiResult<Map<String, Object>> create(@RequestBody KbService.ArticleReq req) {
        return ApiResult.ok(this.kb.save(null, req, requireUser().username()));
    }

    @PutMapping({"/{id}"})
    @RequirePerm(
        module = "knowledge",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "knowledge",
        objType = "kb_article",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> update(@PathVariable Long id, @RequestBody KbService.ArticleReq req) {
        return ApiResult.ok(this.kb.save(id, req, requireUser().username()));
    }

    @DeleteMapping({"/{id}"})
    @RequirePerm(
        module = "knowledge",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "knowledge",
        objType = "kb_article",
        objIdSpEL = "#id"
    )
    public ApiResult<Void> delete(@PathVariable Long id) {
        this.kb.delete(id);
        return ApiResult.ok();
    }

    @PostMapping({"/{id}/feedback"})
    @RequirePerm(
        module = "knowledge",
        write = true,
        action = "feedback"
    )
    public ApiResult<Map<String, Object>> feedback(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean helpful = body == null ? null : body.get("helpful");
        if (helpful == null) {
            throw BusinessException.badRequest("请指定 helpful=true/false");
        } else {
            return ApiResult.ok(this.kb.feedback(id, helpful, requireUser().username()));
        }
    }

    private static boolean canWrite() {
        LoginUser u = CurrentUser.get();
        if (u == null) {
            return false;
        } else if (u.isAdmin()) {
            return true;
        } else {
            LoginUser.ModulePerm p = u.modulePerms() == null ? null : u.modulePerms().get("knowledge");
            return p != null && p.write();
        }
    }

    private static LoginUser requireUser() {
        LoginUser u = CurrentUser.get();
        if (u == null) {
            throw BusinessException.unauthorized("未登录或会话已过期");
        } else {
            return u;
        }
    }

    
    public KbController(final KbService kb) {
        this.kb = kb;
    }
}
