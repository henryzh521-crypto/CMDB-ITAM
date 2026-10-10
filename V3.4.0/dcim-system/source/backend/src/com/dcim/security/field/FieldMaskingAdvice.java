package com.dcim.security.field;

import com.dcim.common.ApiResult;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 字段级权限·读（v3.4）：在响应统一出口剔除当前用户不可见的字段，并在响应头 X-Field-Hidden 声明剔除了哪些。
 * 管理员、无字段限制的用户不做任何处理（零开销）。
 */
@ControllerAdvice
public class FieldMaskingAdvice implements ResponseBodyAdvice<Object> {
    private final FieldPolicyService policies;
    private final ObjectMapper objectMapper;

    public FieldMaskingAdvice(FieldPolicyService policies, ObjectMapper objectMapper) {
        this.policies = policies;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof ApiResult<?> api) || api.getData() == null) {
            return body;
        }
        FieldRouteRules.Rule rule = FieldRouteRules.match(request.getMethod().name(), request.getURI().getPath());
        if (rule == null || rule.mode() != FieldRouteRules.Mode.MASK) {
            return body;
        }
        LoginUser user = CurrentUser.get();
        FieldPolicy policy = this.policies.policy(user, rule.module());
        if (policy.hidden().isEmpty()) {
            return body;
        }
        Set<String> hiddenLower = policy.hiddenKeysLower(rule.module());
        JsonNode data = this.objectMapper.valueToTree(api.getData());
        FieldPolicyService.mask(data, rule.module(), hiddenLower);
        response.getHeaders().set("X-Field-Hidden", String.join(",", new java.util.TreeSet<>(policy.hidden())));
        return new ApiResult<>(api.getCode(), api.getMsg(), data);
    }
}
