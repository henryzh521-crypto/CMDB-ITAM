package com.dcim.system.openapi;

import com.dcim.common.ApiResult;
import com.dcim.common.BuildInfo;
import com.dcim.common.PageResult;
import com.dcim.security.ModuleRegistry;
import com.dcim.security.RequirePerm;
import com.dcim.security.field.FieldRouteRules;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * OpenAPI 3.0 接口文档（v3.4，ADR-06）：运行时从 Spring MVC 路由与 Jackson 反序列化元数据生成，登录后可访问。
 *
 * <p>不引入 springdoc：离线环境无法新增依赖，且它要求在控制器上逐个加注解。代价是多数控制器返回 Map，
 * 响应体内部结构推导不到，文档只给出统一外层 {@code {code, msg, data}}。
 * 每个接口带 {@code x-permission}（模块、读写、操作），字段级权限覆盖的接口带 {@code x-field-policy}。
 */
@RestController
public class OpenApiController {
    private final RequestMappingHandlerMapping mappings;
    private final ObjectMapper objectMapper;
    private final ModuleRegistry modules;
    private volatile Map<String, Object> cached;

    public OpenApiController(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings, ObjectMapper objectMapper,
                             ModuleRegistry modules) {
        this.mappings = mappings;
        this.objectMapper = objectMapper;
        this.modules = modules;
    }

    @GetMapping({"/api/openapi.json"})
    public Map<String, Object> openapi() {
        Map<String, Object> c = this.cached;
        if (c == null) {
            synchronized (this) {
                c = this.cached;
                if (c == null) {
                    c = this.build();
                    this.cached = c;
                }
            }
        }
        return c;
    }

    // ───────────────────────── 构建 ─────────────────────────

    Map<String, Object> build() {
        Map<String, Object> schemas = new TreeMap<>();
        Map<String, Map<String, Object>> paths = new TreeMap<>();
        Map<String, String> tagNames = new TreeMap<>();
        List<Map.Entry<RequestMappingInfo, HandlerMethod>> entries = new ArrayList<>(this.mappings.getHandlerMethods().entrySet());
        entries.sort(Comparator.comparing(e -> e.getValue().toString()));
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : entries) {
            RequestMappingInfo info = e.getKey();
            HandlerMethod hm = e.getValue();
            if (!hm.getBeanType().getName().startsWith("com.dcim.")) {
                continue;
            }
            Set<String> patterns = info.getPatternValues();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String path : patterns) {
                if (!path.startsWith("/api/")) {
                    continue;
                }
                for (RequestMethod m : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    Map<String, Object> op = this.operation(hm, m.name(), path, schemas, tagNames);
                    paths.computeIfAbsent(path, k -> new TreeMap<>()).put(m.name().toLowerCase(), op);
                }
            }
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("openapi", "3.0.3");
        doc.put("info", Map.of(
            "title", "智算资产管理平台 API",
            "version", BuildInfo.version(),
            "description", "统一响应结构 {code, msg, data}：code=0 成功；HTTP 401 未登录、403 无权限、404 不存在或不可见、409 冲突、429 限流。"
                + "认证使用登录接口下发的 access_token Cookie，或请求头 Authorization: Bearer <个人访问令牌>。"
        ));
        List<Map<String, Object>> tags = new ArrayList<>();
        tagNames.forEach((code, name) -> tags.add(Map.of("name", code, "description", name)));
        doc.put("tags", tags);
        doc.put("paths", paths);
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("schemas", schemas);
        components.put("securitySchemes", Map.of(
            "cookieAuth", Map.of("type", "apiKey", "in", "cookie", "name", "access_token"),
            "bearerAuth", Map.of("type", "http", "scheme", "bearer")
        ));
        doc.put("components", components);
        doc.put("security", List.of(Map.of("cookieAuth", List.of()), Map.of("bearerAuth", List.of())));
        return doc;
    }

    private Map<String, Object> operation(HandlerMethod hm, String method, String path, Map<String, Object> schemas, Map<String, String> tagNames) {
        Map<String, Object> op = new LinkedHashMap<>();
        RequirePerm perm = hm.getMethodAnnotation(RequirePerm.class);
        String tag = perm != null ? perm.module() : hm.getBeanType().getSimpleName().replace("Controller", "");
        tagNames.putIfAbsent(tag, perm != null ? this.modules.nameOf(perm.module()) : tag);
        op.put("tags", List.of(tag));
        op.put("operationId", hm.getBeanType().getSimpleName() + "." + hm.getMethod().getName());
        op.put("summary", method + " " + path);
        if (perm != null) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("module", perm.module());
            x.put("moduleName", this.modules.nameOf(perm.module()));
            x.put("access", perm.write() ? "write" : "read");
            if (!perm.action().isEmpty()) {
                x.put("action", perm.action());
            }
            op.put("x-permission", x);
        }
        FieldRouteRules.Rule rule = FieldRouteRules.match(method, path.replaceAll("\\{[^/]+}", "X0"));
        if (rule != null) {
            op.put("x-field-policy", Map.of("module", rule.module(), "mode", rule.mode() == null ? "REQUIRE" : rule.mode().name()));
        }
        List<Map<String, Object>> params = new ArrayList<>();
        Map<String, Object> body = null;
        for (MethodParameter mp : hm.getMethodParameters()) {
            Parameter p = mp.getMethod().getParameters()[mp.getParameterIndex()];
            PathVariable pv = mp.getParameterAnnotation(PathVariable.class);
            RequestParam rp = mp.getParameterAnnotation(RequestParam.class);
            RequestBody rb = mp.getParameterAnnotation(RequestBody.class);
            RequestPart part = mp.getParameterAnnotation(RequestPart.class);
            if (pv != null) {
                params.add(param(firstNonEmpty(pv.value(), pv.name(), p.getName()), "path", true, this.schemaOf(mp.getGenericParameterType(), schemas, 0)));
            } else if (rp != null && MultipartFile.class.isAssignableFrom(mp.getParameterType())) {
                body = Map.of("required", true, "content", Map.of("multipart/form-data", Map.of("schema", Map.of(
                    "type", "object", "properties", Map.of(firstNonEmpty(rp.value(), rp.name(), "file"), Map.of("type", "string", "format", "binary"))))));
            } else if (rp != null) {
                Map<String, Object> s = new LinkedHashMap<>(this.schemaOf(mp.getGenericParameterType(), schemas, 0));
                if (!org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE.equals(rp.defaultValue())) {
                    s.put("default", rp.defaultValue());
                }
                boolean required = rp.required() && org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE.equals(rp.defaultValue());
                params.add(param(firstNonEmpty(rp.value(), rp.name(), p.getName()), "query", required, s));
            } else if (rb != null) {
                body = new LinkedHashMap<>();
                body.put("required", rb.required());
                body.put("content", Map.of("application/json", Map.of("schema", this.schemaOf(mp.getGenericParameterType(), schemas, 0))));
            } else if (part != null) {
                body = Map.of("content", Map.of("multipart/form-data", Map.of("schema", Map.of("type", "object"))));
            }
        }
        if (!params.isEmpty()) {
            op.put("parameters", params);
        }
        if (body != null) {
            op.put("requestBody", body);
        }
        op.put("responses", Map.of("200", this.response(hm, schemas)));
        return op;
    }

    private Map<String, Object> response(HandlerMethod hm, Map<String, Object> schemas) {
        ResolvableType rt = ResolvableType.forMethodReturnType(hm.getMethod());
        Class<?> raw = rt.resolve();
        if (raw == null || raw == void.class || raw == Void.class) {
            return Map.of("description", "成功（直接写响应流，如文件下载）");
        }
        if (ResponseEntity.class.isAssignableFrom(raw)) {
            return Map.of("description", "成功（文件或原始内容）");
        }
        Map<String, Object> schema;
        if (ApiResult.class.isAssignableFrom(raw)) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("code", Map.of("type", "integer", "example", 0));
            props.put("msg", Map.of("type", "string", "example", "ok"));
            props.put("data", this.schemaOf(rt.getGeneric(0), schemas, 0));
            schema = Map.of("type", "object", "properties", props);
        } else {
            schema = this.schemaOf(rt, schemas, 0);
        }
        return Map.of("description", "成功", "content", Map.of("application/json", Map.of("schema", schema)));
    }

    private static Map<String, Object> param(String name, String in, boolean required, Map<String, Object> schema) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", name);
        p.put("in", in);
        p.put("required", required);
        p.put("schema", schema);
        return p;
    }

    private static String firstNonEmpty(String... v) {
        for (String s : v) {
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        return "arg";
    }

    private Map<String, Object> schemaOf(Type type, Map<String, Object> schemas, int depth) {
        return this.schemaOf(ResolvableType.forType(type), schemas, depth);
    }

    private Map<String, Object> schemaOf(ResolvableType rt, Map<String, Object> schemas, int depth) {
        Class<?> c = rt.resolve(Object.class);
        if (c == String.class || c == Character.class || c == char.class || c.isEnum()) {
            return Map.of("type", "string");
        }
        if (c == Integer.class || c == int.class || c == Long.class || c == long.class || c == Short.class || c == short.class) {
            return Map.of("type", "integer");
        }
        if (c == BigDecimal.class || c == Double.class || c == double.class || c == Float.class || c == float.class) {
            return Map.of("type", "number");
        }
        if (c == Boolean.class || c == boolean.class) {
            return Map.of("type", "boolean");
        }
        if (c == LocalDate.class) {
            return Map.of("type", "string", "format", "date");
        }
        if (c == LocalDateTime.class || c == OffsetDateTime.class) {
            return Map.of("type", "string", "format", "date-time");
        }
        if (Collection.class.isAssignableFrom(c) || c.isArray()) {
            ResolvableType item = c.isArray() ? rt.getComponentType() : rt.asCollection().getGeneric(0);
            return Map.of("type", "array", "items", depth > 4 ? Map.of() : this.schemaOf(item, schemas, depth + 1));
        }
        if (Map.class.isAssignableFrom(c) || c == Object.class) {
            return Map.of("type", "object");
        }
        if (PageResult.class.isAssignableFrom(c)) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("records", Map.of("type", "array", "items", depth > 4 ? Map.of() : this.schemaOf(rt.getGeneric(0), schemas, depth + 1)));
            props.put("total", Map.of("type", "integer"));
            props.put("page", Map.of("type", "integer"));
            props.put("size", Map.of("type", "integer"));
            return Map.of("type", "object", "properties", props);
        }
        if (!c.getName().startsWith("com.dcim.") || depth > 6) {
            return Map.of("type", "object");
        }
        String name = c.getName().substring(c.getName().lastIndexOf('.') + 1).replace('$', '.');
        if (!schemas.containsKey(name)) {
            schemas.put(name, Map.of("type", "object")); // 先占位，防递归
            schemas.put(name, this.beanSchema(c, schemas, depth));
        }
        return Map.of("$ref", "#/components/schemas/" + name);
    }

    private Map<String, Object> beanSchema(Class<?> c, Map<String, Object> schemas, int depth) {
        JavaType jt = this.objectMapper.constructType(c);
        BeanDescription desc = this.objectMapper.getDeserializationConfig().introspect(jt);
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (BeanPropertyDefinition def : desc.findProperties()) {
            if (!def.couldDeserialize()) {
                continue;
            }
            Type t = def.getField() != null ? def.getField().getAnnotated().getGenericType()
                : def.getSetter() != null ? def.getSetter().getAnnotated().getGenericParameterTypes()[0]
                : def.getPrimaryType();
            Map<String, Object> s = new LinkedHashMap<>(this.schemaOf(t, schemas, depth + 1));
            List<PropertyName> aliases = def.findAliases();
            if (aliases != null && !aliases.isEmpty()) {
                s.put("x-aliases", aliases.stream().map(PropertyName::getSimpleName).toList());
            }
            if (def.getField() != null) {
                for (Annotation a : def.getField().getAnnotated().getAnnotations()) {
                    if (a instanceof NotBlank || a instanceof NotNull || a instanceof NotEmpty) {
                        required.add(def.getName());
                        String msg = a instanceof NotBlank nb ? nb.message() : a instanceof NotNull nn ? nn.message() : ((NotEmpty)a).message();
                        if (msg != null && !msg.startsWith("{")) {
                            s.put("description", msg);
                        }
                        break;
                    }
                }
            }
            props.put(def.getName(), s);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "object");
        out.put("properties", props);
        if (!required.isEmpty()) {
            out.put("required", required);
        }
        return out;
    }
}
