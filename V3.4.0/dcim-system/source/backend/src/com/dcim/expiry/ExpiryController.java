package com.dcim.expiry;

import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/expiry"})
public class ExpiryController {
    private final ObjectProvider<ExpiryService> service;

    @GetMapping
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<Map<String, Object>> upcoming(@RequestParam(defaultValue = "90") int days, @RequestParam(required = false) String kind) {
        if (kind != null && !kind.isBlank() && !"WARRANTY".equals(kind) && !"CONTRACT".equals(kind)) {
            throw BusinessException.badRequest("kind 须为 WARRANTY / CONTRACT");
        } else {
            return ApiResult.ok(this.require().upcoming(days, kind != null && !kind.isBlank() ? kind : null));
        }
    }

    @PostMapping({"/run"})
    @RequirePerm(
        module = "assets",
        write = true,
        action = "update"
    )
    @Audit(
        action = "RUN",
        module = "assets",
        objType = "expiry_notice"
    )
    public ApiResult<Map<String, Object>> run() {
        LoginUser u = CurrentUser.get();
        if (u != null && u.isAdmin()) {
            return ApiResult.ok(this.require().run());
        } else {
            throw BusinessException.forbidden("仅管理员可手动触发提醒");
        }
    }

    private ExpiryService require() {
        ExpiryService s = (ExpiryService)this.service.getIfAvailable();
        if (s == null) {
            throw BusinessException.badRequest("到期提醒已在部署配置中关闭（dcim.expiry.enabled=false）");
        } else {
            return s;
        }
    }

    
    public ExpiryController(final ObjectProvider<ExpiryService> service) {
        this.service = service;
    }
}
