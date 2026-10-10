package com.dcim.catalog;

import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
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
@RequestMapping({"/api/catalog"})
public class CatalogController {
    private final CatalogService catalog;

    @GetMapping({"/items"})
    public ApiResult<List<Map<String, Object>>> items(@RequestParam(defaultValue = "false") boolean all) {
        return ApiResult.ok(this.catalog.items(user(), all));
    }

    @GetMapping({"/items/{id}"})
    public ApiResult<Map<String, Object>> item(@PathVariable Long id) {
        return ApiResult.ok(this.catalog.item(id, user()));
    }

    @PostMapping({"/items/{id}/request"})
    @Audit(
        action = "REQUEST",
        module = "catalog",
        objType = "catalog_item",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> request(@PathVariable Long id, @RequestBody Map<String, Object> values) {
        return ApiResult.ok(this.catalog.request(id, values, user()));
    }

    @GetMapping({"/my-requests"})
    public ApiResult<Map<String, Object>> my(@RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(this.catalog.myRequests(user().username(), page, size));
    }

    @GetMapping({"/wo-types"})
    public ApiResult<List<String>> woTypes() {
        admin();
        return ApiResult.ok(this.catalog.woTypes());
    }

    @PostMapping({"/items"})
    @Audit(
        action = "CREATE",
        module = "catalog",
        objType = "catalog_item",
        objIdSpEL = "#req.code()"
    )
    public ApiResult<Map<String, Object>> create(@RequestBody CatalogService.ItemReq req) {
        admin();
        return ApiResult.ok(Map.of("id", this.catalog.save(null, req)));
    }

    @PutMapping({"/items/{id}"})
    @Audit(
        action = "UPDATE",
        module = "catalog",
        objType = "catalog_item",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> update(@PathVariable Long id, @RequestBody CatalogService.ItemReq req) {
        admin();
        return ApiResult.ok(Map.of("id", this.catalog.save(id, req)));
    }

    @DeleteMapping({"/items/{id}"})
    @Audit(
        action = "DELETE",
        module = "catalog",
        objType = "catalog_item",
        objIdSpEL = "#id"
    )
    public ApiResult<Void> delete(@PathVariable Long id) {
        admin();
        this.catalog.delete(id);
        return ApiResult.ok();
    }

    private static LoginUser user() {
        LoginUser u = CurrentUser.get();
        if (u == null) {
            throw BusinessException.unauthorized("未登录或会话已过期");
        } else {
            return u;
        }
    }

    private static void admin() {
        if (!user().isAdmin()) {
            throw BusinessException.forbidden("仅管理员可维护服务目录");
        }
    }

    
    public CatalogController(final CatalogService catalog) {
        this.catalog = catalog;
    }
}
