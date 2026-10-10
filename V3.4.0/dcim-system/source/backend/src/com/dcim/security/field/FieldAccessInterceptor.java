package com.dcim.security.field;

import com.dcim.common.BusinessException;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 字段级权限·整接口依赖（v3.4）：工单附件接口依赖「附件」字段、工单资产行接口依赖「关联资产」字段、
 * 批量导入要求无任何字段限制。这些接口没有可逐字段比对的 JSON 请求体，放在拦截器里统一判断。
 */
@Configuration
public class FieldAccessInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private final FieldPolicyService policies;

    public FieldAccessInterceptor(FieldPolicyService policies) {
        this.policies = policies;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // v3.5：生命周期活动接口返回资产字段（assetNo/statusBiz 等），纳入字段权限拦截范围
        registry.addInterceptor(this).addPathPatterns("/api/assets/**", "/api/workorders/**", "/api/asset-lifecycle/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        FieldRouteRules.Rule rule = FieldRouteRules.match(request.getMethod(), request.getRequestURI());
        if (rule == null) {
            return true;
        }
        LoginUser user = CurrentUser.get();
        if (rule.unrestrictedOnly()) {
            this.policies.assertUnrestricted(user, rule.module(), "批量导入");
        }
        FieldPolicy p = this.policies.policy(user, rule.module());
        if (p.isEmpty()) {
            return true;
        }
        for (String f : rule.requireVisible()) {
            if (p.isHidden(f)) {
                throw BusinessException.forbidden("无权查看「" + FieldCatalog.label(rule.module(), f) + "」");
            }
        }
        for (String f : rule.requireWritable()) {
            if (p.isLocked(f)) {
                throw BusinessException.forbidden("无权修改字段：" + FieldCatalog.label(rule.module(), f));
            }
        }
        return true;
    }
}
