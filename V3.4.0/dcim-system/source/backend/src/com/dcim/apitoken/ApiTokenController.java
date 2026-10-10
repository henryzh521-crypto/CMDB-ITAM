package com.dcim.apitoken;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/tokens"})
public class ApiTokenController {
    private final ApiTokenService tokens;

    @GetMapping
    public ApiResult<List<Map<String, Object>>> list(@RequestParam(defaultValue = "false") boolean all) {
        LoginUser u = user();
        return ApiResult.ok(this.tokens.list(all && u.isAdmin() ? null : u.username()));
    }

    @PostMapping
    @Audit(
        action = "CREATE",
        module = "system",
        objType = "api_token",
        objIdSpEL = "#req.name()"
    )
    public ApiResult<Map<String, Object>> create(@RequestBody ApiTokenController.CreateReq req) {
        LoginUser u = user();
        boolean readOnly = req.readOnly() == null || req.readOnly();
        return ApiResult.ok(this.tokens.create(u.username(), req.name(), readOnly, req.expireDays(), u.username()));
    }

    @DeleteMapping({"/{id}"})
    @Audit(
        action = "REVOKE",
        module = "system",
        objType = "api_token",
        objIdSpEL = "#id"
    )
    public ApiResult<Void> revoke(@PathVariable Long id) {
        LoginUser u = user();
        this.tokens.revoke(id, u.isAdmin() ? null : u.username());
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

    
    public ApiTokenController(final ApiTokenService tokens) {
        this.tokens = tokens;
    }

    public static record CreateReq(String name, Boolean readOnly, Integer expireDays) {
    }
}
