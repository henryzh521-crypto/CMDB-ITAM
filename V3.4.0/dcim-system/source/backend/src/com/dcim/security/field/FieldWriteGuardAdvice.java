package com.dcim.security.field;

import com.dcim.asset.entity.AssetEntity;
import com.dcim.asset.mapper.AssetMapper;
import com.dcim.security.CurrentUser;
import com.dcim.workorder.entity.WorkorderEntity;
import com.dcim.workorder.mapper.WorkorderMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/**
 * 字段级权限·写（v3.4）：JSON 请求体进入控制器前，与当前值比对；改动了当前用户隐藏 / 只读的字段即 403。
 * 前端编辑表单把整条记录原样回传（含只读字段的原值）不算改动，不受影响。
 */
@ControllerAdvice
public class FieldWriteGuardAdvice extends RequestBodyAdviceAdapter {
    private final FieldPolicyService policies;
    private final ObjectMapper objectMapper;
    private final AssetMapper assetMapper;
    private final WorkorderMapper workorderMapper;

    public FieldWriteGuardAdvice(FieldPolicyService policies, ObjectMapper objectMapper, AssetMapper assetMapper, WorkorderMapper workorderMapper) {
        this.policies = policies;
        this.objectMapper = objectMapper;
        this.assetMapper = assetMapper;
        this.workorderMapper = workorderMapper;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            return inputMessage;
        }
        String path = req.getRequestURI();
        FieldRouteRules.Rule rule = FieldRouteRules.match(req.getMethod(), path);
        if (rule == null || rule.mode() == null || rule.mode() == FieldRouteRules.Mode.MASK) {
            return inputMessage;
        }
        FieldPolicy policy = this.policies.policy(CurrentUser.get(), rule.module());
        if (policy.isEmpty()) {
            return inputMessage;
        }
        byte[] bytes = inputMessage.getBody().readAllBytes();
        JsonNode body;
        try {
            body = this.objectMapper.readTree(bytes);
        } catch (IOException e) {
            return replay(inputMessage.getHeaders(), bytes); // 交给后续 Jackson 报格式错误
        }
        JsonNode current = null;
        if (rule.mode() == FieldRouteRules.Mode.UPDATE) {
            current = this.loadCurrent(rule, path);
        }
        if (body != null && body.isArray()) {
            for (JsonNode e : body) {
                FieldPolicyService.checkWrite(rule.module(), policy, e, null, rule.aliases());
            }
        } else {
            FieldPolicyService.checkWrite(rule.module(), policy, body, current, rule.aliases());
        }
        return replay(inputMessage.getHeaders(), bytes);
    }

    private JsonNode loadCurrent(FieldRouteRules.Rule rule, String path) {
        Map<String, String> vars = FieldRouteRules.vars(rule, path);
        Object entity = null;
        if (FieldCatalog.ASSETS.equals(rule.module()) && vars.get("assetNo") != null) {
            AssetEntity a = (AssetEntity)this.assetMapper.selectById(vars.get("assetNo"));
            entity = a;
        } else if (FieldCatalog.WORKORDERS.equals(rule.module()) && vars.get("woNo") != null) {
            entity = this.workorderMapper.selectOne(Wrappers.<WorkorderEntity>lambdaQuery().eq(WorkorderEntity::getWoNo, vars.get("woNo")));
        }
        // 记录不存在时按「新建」处理：受限字段有值即拒绝；随后控制器会返回 404
        return entity == null ? null : this.objectMapper.valueToTree(entity);
    }

    private static HttpInputMessage replay(HttpHeaders headers, byte[] bytes) {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? a.getRequest() : null;
    }
}
