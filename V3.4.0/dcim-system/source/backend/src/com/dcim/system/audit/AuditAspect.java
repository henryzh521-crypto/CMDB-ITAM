package com.dcim.system.audit;

import com.dcim.common.ApiResult;
import com.dcim.security.ClientIp;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

@Aspect
@Component
public class AuditAspect {
    
    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final int DETAIL_MAX_CHARS = 2048;
    private static final String MASK = "***";
    private static final Pattern SENSITIVE_KEY = Pattern.compile("password|passwd|pwd|token|secret|credential|api[-_]?key|private[-_]?key|authorization", 2);
    private final AuditService auditService;
    private final ClientIp clientIpResolver;
    /** v3.4：注册 Java 时间类型模块。原来的裸 ObjectMapper 不认识 LocalDateTime，带时间字段的返回值全部记为「无法序列化」 */
    private final ObjectMapper objectMapper = new ObjectMapper()
        .findAndRegisterModules()
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    /** 未声明 objIdSpEL 时，从返回值里按这些键推断对象号（新建类操作此前没有对象号） */
    private static final java.util.List<String> ID_KEYS = java.util.List.of(
        "woNo", "assetNo", "prbNo", "chgNo", "changeNo", "kbNo", "planNo", "roleCode", "username", "code", "id"
    );
    private final ExpressionParser spelParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public AuditAspect(AuditService auditService, ClientIp clientIpResolver) {
        this.auditService = auditService;
        this.clientIpResolver = clientIpResolver;
    }

    @AfterReturning(
        pointcut = "@annotation(audit)",
        returning = "ret"
    )
    public void record(JoinPoint jp, Audit audit, Object ret) {
        try {
            LoginUser user = CurrentUser.get();
            String objId = this.evaluateObjId(jp, audit.objIdSpEL());
            if (objId == null) {
                objId = inferObjId(ret);
            }
            this.auditService
                .record(
                    user == null ? "anonymous" : user.username(),
                    audit.action(),
                    audit.module(),
                    audit.objType().isEmpty() ? null : audit.objType(),
                    objId,
                    buildDetail(jp.getArgs(), ret, this.objectMapper),
                    this.clientIp()
                );
        } catch (Exception var6) {
            log.warn("审计记录失败（不影响业务）: {}", audit.action(), var6);
        }
    }

    static String inferObjId(Object ret) {
        Object data = ret instanceof ApiResult<?> api ? api.getData() : ret;
        if (data instanceof Map<?, ?> m) {
            for (String k : ID_KEYS) {
                Object v = m.get(k);
                if (v instanceof String || v instanceof Number) {
                    String s = String.valueOf(v);
                    return s.length() > 100 ? s.substring(0, 100) : s;
                }
            }
        }
        return null;
    }

    private String evaluateObjId(JoinPoint jp, String expression) {
        if (expression != null && !expression.isBlank()) {
            try {
                MethodSignature signature = (MethodSignature)jp.getSignature();
                MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    jp.getTarget(), signature.getMethod(), jp.getArgs(), this.parameterNameDiscoverer
                );
                Object value = this.spelParser.parseExpression(expression).getValue(context);
                return value == null ? null : String.valueOf(value);
            } catch (Exception var6) {
                log.debug("审计 objIdSpEL 求值失败: {}", expression);
                return null;
            }
        } else {
            return null;
        }
    }

    static Map<String, Object> buildDetail(Object[] args, ObjectMapper mapper) {
        return buildDetail(args, null, mapper);
    }

    static Map<String, Object> buildDetail(Object[] args, Object returnValue, ObjectMapper mapper) {
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            List<Object> kept = new ArrayList<>();
            boolean truncated = false;
            int used = 0;
            if (args != null) {
                for (Object arg : args) {
                    Object safe = sanitize(toPlain(normalizeArg(arg), mapper));
                    String json = mapper.writeValueAsString(safe);
                    if (used + json.length() + 2 > 2048) {
                        int room = Math.max(0, 2048 - used - 32);
                        String slice = json.length() <= room ? json : json.substring(0, room);
                        kept.add(slice + "…(入参过长，已截断)");
                        truncated = true;
                        break;
                    }

                    kept.add(safe);
                    used += json.length() + 2;
                }
            }

            out.put("args", kept);
            if (truncated) {
                out.put("truncated", true);
                out.put("argCount", args == null ? 0 : args.length);
            }

            Object res = normalizeResult(returnValue);
            if (res != null) {
                Object safeRes = sanitize(toPlain(res, mapper));
                String resJson = mapper.writeValueAsString(safeRes);
                if (used + resJson.length() + 2 <= 2048) {
                    out.put("result", safeRes);
                } else {
                    Object slim = slimResult(safeRes);
                    if (slim != null && used + mapper.writeValueAsString(slim).length() + 2 <= 2048) {
                        out.put("result", slim);
                        out.put("resultTrimmed", true);
                    }
                }
            }

            Map<String, Object> sanitized = (Map<String, Object>)sanitize(out);
            if (mapper.writeValueAsString(sanitized).length() > 3072) {
                sanitized.remove("result");
                sanitized.remove("resultTrimmed");
            }

            if (mapper.writeValueAsString(sanitized).length() > 3072) {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("args", "入参过大，已省略");
                fallback.put("argCount", args == null ? 0 : args.length);
                fallback.put("truncated", true);
                return fallback;
            } else {
                return sanitized;
            }
        } catch (Exception var15) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("args", "序列化失败");
            return fallback;
        }
    }

    private static Object normalizeArg(Object arg) {
        if (arg instanceof MultipartFile file) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("filename", file.getOriginalFilename());
            m.put("bytes", file.getSize());
            return m;
        } else {
            return arg;
        }
    }

    private static Object toPlain(Object value, ObjectMapper mapper) {
        if (value != null && !(value instanceof String) && !(value instanceof Number) && !(value instanceof Boolean)) {
            try {
                return mapper.convertValue(value, Object.class);
            } catch (Exception var3) {
                return "[无法序列化，已省略]";
            }
        } else {
            return value;
        }
    }

    private static Object normalizeResult(Object ret) {
        Object data = ret instanceof ApiResult<?> api ? api.getData() : ret;
        return data instanceof Map ? data : null;
    }

    private static Object slimResult(Object res) {
        if (res instanceof Map<?, ?> map && !map.isEmpty()) {
            Map<String, Object> slim = new LinkedHashMap<>();

            for (Entry<?, ?> e : map.entrySet()) {
                Object v = e.getValue();
                if (v instanceof String || v instanceof Number || v instanceof Boolean) {
                    slim.put(String.valueOf(e.getKey()), v);
                }
            }

            return slim.isEmpty() ? null : slim;
        }

        return null;
    }

    private static Object sanitize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();

            for (Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                out.put(key, SENSITIVE_KEY.matcher(key).find() ? "***" : sanitize(e.getValue()));
            }

            return out;
        } else {
            return value instanceof Collection<?> col ? col.stream().map(AuditAspect::sanitize).collect(Collectors.toList()) : value;
        }
    }

    private String clientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes)RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : this.clientIpResolver.resolve(attrs.getRequest());
    }
}
