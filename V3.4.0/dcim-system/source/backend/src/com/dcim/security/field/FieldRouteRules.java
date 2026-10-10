package com.dcim.security.field;

import java.util.List;
import java.util.Map;
import org.springframework.util.AntPathMatcher;

/**
 * 字段级权限的路由登记表（v3.4）：哪些接口的响应要剔除隐藏字段、哪些写接口要比对请求体、
 * 哪些接口整体依赖某个字段（例如工单附件接口依赖「附件」字段）。
 *
 * <p>新增返回资产 / 工单字段的接口需在此登记；测试套件的字段权限用例会逐个接口验证。
 */
public final class FieldRouteRules {
    public enum Mode {
        /** 响应剔除隐藏字段 */
        MASK,
        /** 新建：受限字段有值即拒绝 */
        CREATE,
        /** 更新：与当前值比对，改动受限字段才拒绝 */
        UPDATE,
        /** 只看请求体里出现的受限字段（批量 / 流转 / 费用等） */
        PRESENCE
    }

    public record Rule(String method, String pattern, String module, Mode mode, Map<String, String> aliases,
                       List<String> requireVisible, List<String> requireWritable, boolean unrestrictedOnly) {
    }

    private static final Map<String, String> WO_ASSET_ALIASES = Map.of(
        "assets", "relatedAssets",
        "assetNos", "relatedAssets",
        "assetNoList", "relatedAssets",
        "assetList", "relatedAssets",
        "assetNo", "relatedAssets",
        "relatedAssetNos", "relatedAssets",
        "asset_nos", "relatedAssets",
        "related_assets", "relatedAssets",
        "targetRack", "targetLocation",
        "targetUnit", "targetLocation"
    );
    private static final Map<String, String> ASSET_TRANSITION_ALIASES = Map.of("targetBiz", "statusBiz", "targetFin", "statusFin");

    private static final List<Rule> RULES = List.of(
        // ── 资产：读
        read("/api/assets", "assets"),
        read("/api/assets/tree", "assets"),
        read("/api/assets/lookup", "assets"),
        read("/api/assets/{assetNo}", "assets"),
        read("/api/assets/{assetNo}/children", "assets"),
        read("/api/assets/{assetNo}/events", "assets"),
        // ── 资产：写
        new Rule("POST", "/api/assets", "assets", Mode.CREATE, Map.of(), List.of(), List.of(), false),
        new Rule("PUT", "/api/assets/{assetNo}", "assets", Mode.UPDATE, Map.of(), List.of(), List.of(), false),
        new Rule("POST", "/api/assets/batch-update", "assets", Mode.PRESENCE, Map.of(), List.of(), List.of(), false),
        new Rule("POST", "/api/assets/batch-transitions", "assets", Mode.PRESENCE, ASSET_TRANSITION_ALIASES, List.of(), List.of(), false),
        new Rule("POST", "/api/assets/{assetNo}/transitions", "assets", Mode.PRESENCE, ASSET_TRANSITION_ALIASES, List.of(), List.of(), false),
        new Rule("POST", "/api/assets/import", "assets", null, Map.of(), List.of(), List.of(), true),
        // ── 工单：读
        read("/api/workorders", "workorders"),
        read("/api/workorders/{woNo}", "workorders"),
        new Rule("GET", "/api/workorders/{woNo}/attachments", "workorders", null, Map.of(), List.of("attachments"), List.of(), false),
        new Rule("GET", "/api/workorders/{woNo}/attachments/**", "workorders", null, Map.of(), List.of("attachments"), List.of(), false),
        // ── 工单：写
        new Rule("POST", "/api/workorders", "workorders", Mode.CREATE, WO_ASSET_ALIASES, List.of(), List.of(), false),
        new Rule("PUT", "/api/workorders/{woNo}", "workorders", Mode.UPDATE, WO_ASSET_ALIASES, List.of(), List.of(), false),
        new Rule("PUT", "/api/workorders/{woNo}/repair-cost", "workorders", Mode.PRESENCE, Map.of(), List.of(), List.of(), false),
        new Rule("POST", "/api/workorders/{woNo}/transitions", "workorders", Mode.PRESENCE, Map.of(), List.of(), List.of(), false),
        new Rule("POST", "/api/workorders/{woNo}/items", "workorders", null, Map.of(), List.of(), List.of("relatedAssets"), false),
        new Rule("PUT", "/api/workorders/{woNo}/items/{itemNo}", "workorders", null, Map.of(), List.of(), List.of("relatedAssets"), false),
        new Rule("DELETE", "/api/workorders/{woNo}/items/{itemNo}", "workorders", null, Map.of(), List.of(), List.of("relatedAssets"), false),
        new Rule("PUT", "/api/workorders/{woNo}/replacements", "workorders", null, Map.of(), List.of(), List.of("relatedAssets"), false),
        new Rule("POST", "/api/workorders/{woNo}/attachments", "workorders", null, Map.of(), List.of(), List.of("attachments"), false),
        new Rule("DELETE", "/api/workorders/{woNo}/attachments/{id}", "workorders", null, Map.of(), List.of(), List.of("attachments"), false)
    );

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    /** 与 {assetNo} 等变量同形、但实际是固定子路径的段，避免把 /api/assets/meta 当成某资产详情 */
    private static final List<String> RESERVED = List.of(
        "meta", "export", "template", "model-codes", "lookup", "tree", "import", "batch-update", "batch-delete", "batch-transitions",
        "sla-config", "workflow-config", "approver-options", "transfer-locations"
    );

    private FieldRouteRules() {
    }

    private static Rule read(String pattern, String module) {
        return new Rule("GET", pattern, module, Mode.MASK, Map.of(), List.of(), List.of(), false);
    }

    public static Rule match(String method, String path) {
        if (path == null || method == null) {
            return null;
        }
        String p = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        for (Rule r : RULES) {
            if (!r.method().equalsIgnoreCase(method) || !MATCHER.match(r.pattern(), p)) {
                continue;
            }
            if (r.pattern().contains("{")) {
                Map<String, String> vars = MATCHER.extractUriTemplateVariables(r.pattern(), p);
                if (vars.values().stream().anyMatch(RESERVED::contains)) {
                    continue;
                }
            }
            return r;
        }
        return null;
    }

    public static Map<String, String> vars(Rule r, String path) {
        return MATCHER.extractUriTemplateVariables(r.pattern(), path);
    }

    public static List<Rule> all() {
        return RULES;
    }
}
