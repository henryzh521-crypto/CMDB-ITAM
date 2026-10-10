package com.dcim.security.ldap;

import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.system.audit.Audit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/system/ldap"})
public class LdapController {
    private final LdapAuthService ldap;

    @GetMapping
    public ApiResult<Map<String, Object>> summary() {
        admin();
        return ApiResult.ok(this.ldap.summary());
    }

    @PostMapping({"/test"})
    @Audit(
        action = "LDAP_TEST",
        module = "system",
        objType = "ldap",
        objIdSpEL = "#req.username()"
    )
    public ApiResult<Map<String, Object>> test(@RequestBody LdapController.TestReq req) {
        admin();
        if (!this.ldap.enabled()) {
            throw BusinessException.badRequest("未启用 LDAP（设置 LDAP_ENABLED=true 与 LDAP_URL 等后重启）");
        } else if (req.username() != null && !req.username().isBlank()) {
            LdapAuthService.DirUser u = req.password() != null && !req.password().isEmpty()
                ? this.ldap.authenticate(req.username(), req.password())
                : this.ldap.find(req.username());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("found", u != null);
            out.put("authenticated", u != null && req.password() != null && !req.password().isEmpty());
            if (u != null) {
                out.put("dn", u.dn());
                out.put("name", u.name());
                out.put("email", u.email());
                out.put("department", u.department());
                out.put("groups", u.groups());
                out.put("mappedRoles", u.mappedRoles());
            }

            return ApiResult.ok(out);
        } else {
            throw BusinessException.badRequest("请填写测试用户名");
        }
    }

    private static void admin() {
        LoginUser u = CurrentUser.get();
        if (u == null || !u.isAdmin()) {
            throw BusinessException.forbidden("仅管理员可操作");
        }
    }

    
    public LdapController(final LdapAuthService ldap) {
        this.ldap = ldap;
    }

    public static record TestReq(String username, String password) {
    }
}
