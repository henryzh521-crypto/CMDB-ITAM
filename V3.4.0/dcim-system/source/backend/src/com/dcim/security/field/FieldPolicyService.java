package com.dcim.security.field;

import com.dcim.common.BusinessException;
import com.dcim.security.LoginUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 字段级权限（v3.4）：策略合并、响应剔除、写入比对、导出列过滤。
 *
 * <p>v3.3 及以前字段权限「只存不用」：角色里能配置，任何接口都不读取；多角色合并取并集（最严），
 * 与数据范围的「取最宽」矛盾。这里统一为：
 * <ul>
 *   <li>可见：任一授予该模块访问权（读或写）的角色未隐藏该字段，即可见；</li>
 *   <li>可编辑：任一拥有该模块写权限的角色既未隐藏、也未设只读，即可编辑；</li>
 *   <li>管理员不受字段规则约束。</li>
 * </ul>
 */
@Service
public class FieldPolicyService {

    /**
     * 多角色合并。
     * @param roleModuleAccess 角色 → 模块 → [可读, 可写]
     * @param roleFieldCfg     角色 → 模块 → {hidden:[...], readonly:[...]}
     * @return 模块 → {hidden, readonly}（仅包含有限制的模块）
     */
    public static Map<String, Map<String, Object>> merge(
        Map<String, Map<String, boolean[]>> roleModuleAccess, Map<String, Map<String, Map<String, Object>>> roleFieldCfg
    ) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (String module : FieldCatalog.all().keySet()) {
            Set<String> hidden = null;
            Set<String> locked = null;
            for (Entry<String, Map<String, boolean[]>> e : roleModuleAccess.entrySet()) {
                boolean[] rw = e.getValue().get(module);
                if (rw == null || !(rw[0] || rw[1])) {
                    continue;
                }
                Map<String, Object> cfg = roleFieldCfg.getOrDefault(e.getKey(), Map.of()).get(module);
                Set<String> h = lower(cfg == null ? null : cfg.get("hidden"));
                Set<String> r = lower(cfg == null ? null : cfg.get("readonly"));
                hidden = hidden == null ? new HashSet<>(h) : intersect(hidden, h);
                if (rw[1]) {
                    Set<String> hr = new HashSet<>(h);
                    hr.addAll(r);
                    locked = locked == null ? hr : intersect(locked, hr);
                }
            }
            if (hidden == null) {
                continue;
            }
            Set<String> readonly = locked == null ? new HashSet<>() : new HashSet<>(locked);
            readonly.removeAll(hidden);
            if (!hidden.isEmpty() || !readonly.isEmpty()) {
                out.put(module, FieldPolicy.of(canonical(module, hidden), canonical(module, readonly)).toMap());
            }
        }
        return out;
    }

    private static Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.retainAll(b);
        return out;
    }

    private static Set<String> lower(Object v) {
        Set<String> out = new HashSet<>();
        if (v instanceof Collection<?> c) {
            for (Object o : c) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    out.add(String.valueOf(o).trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return out;
    }

    /** 小写字段名还原为目录里的规范写法；目录外的字段丢弃 */
    private static List<String> canonical(String module, Set<String> lowerNames) {
        List<String> out = new ArrayList<>();
        for (FieldCatalog.FieldDef d : FieldCatalog.of(module)) {
            if (lowerNames.contains(d.field().toLowerCase(Locale.ROOT))) {
                out.add(d.field());
            }
        }
        return out;
    }

    /**
     * 角色保存前规整字段配置：模块须在目录里、字段须已知；同一字段既隐藏又只读时按隐藏处理。
     */
    public static Map<String, Map<String, Object>> normalizeRoleConfig(Map<String, Map<String, Object>> in) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (in == null) {
            return out;
        }
        in.forEach((module, cfg) -> {
            if (cfg == null) {
                return;
            }
            if (!FieldCatalog.supports(module)) {
                throw BusinessException.badRequest("模块「" + module + "」不支持字段级权限；目前支持：" + String.join("、", FieldCatalog.all().keySet()));
            }
            for (String k : cfg.keySet()) {
                if (!"hidden".equals(k) && !"readonly".equals(k)) {
                    throw BusinessException.badRequest("字段权限只支持 hidden / readonly：" + module + "." + k);
                }
            }
            Set<String> h = lower(cfg.get("hidden"));
            Set<String> r = lower(cfg.get("readonly"));
            for (String f : union(h, r)) {
                if (FieldCatalog.find(module, f) == null) {
                    throw BusinessException.badRequest("模块「" + module + "」没有可配置的字段：" + f);
                }
            }
            r.removeAll(h);
            if (!h.isEmpty() || !r.isEmpty()) {
                out.put(module, FieldPolicy.of(canonical(module, h), canonical(module, r)).toMap());
            }
        });
        return out;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.addAll(b);
        return out;
    }

    public FieldPolicy policy(LoginUser user, String module) {
        if (user == null || user.isAdmin() || user.fieldPerms() == null || !FieldCatalog.supports(module)) {
            return FieldPolicy.NONE;
        }
        Map<String, Object> cfg = user.fieldPerms().get(module);
        if (cfg == null) {
            return FieldPolicy.NONE;
        }
        return FieldPolicy.of(asList(cfg.get("hidden")), asList(cfg.get("readonly")));
    }

    private static List<?> asList(Object v) {
        return v instanceof List<?> l ? l : List.of();
    }

    public boolean isHidden(LoginUser user, String module, String field) {
        return this.policy(user, module).isHidden(field);
    }

    /** 批量导入、整表覆盖等无法逐字段比对的操作：有任何字段限制就拒绝 */
    public void assertUnrestricted(LoginUser user, String module, String what) {
        FieldPolicy p = this.policy(user, module);
        if (!p.isEmpty()) {
            throw BusinessException.forbidden("当前角色在「" + FieldCatalog.moduleLabel(module) + "」上有字段限制，不能" + what + "，请联系管理员处理");
        }
    }

    /** 列表筛选条件用到了隐藏字段 → 403（否则可借筛选反推隐藏字段的值） */
    public void assertFilterAllowed(LoginUser user, String module, Map<String, Object> filters) {
        FieldPolicy p = this.policy(user, module);
        if (p.hidden().isEmpty() || filters == null) {
            return;
        }
        for (Entry<String, Object> e : filters.entrySet()) {
            Object v = e.getValue();
            if (v != null && !String.valueOf(v).isBlank() && p.isHidden(e.getKey())) {
                throw BusinessException.forbidden("无权按「" + FieldCatalog.label(module, e.getKey()) + "」筛选");
            }
        }
    }

    // ───────────────────────── 读：剔除 ─────────────────────────

    /** 原地剔除隐藏键：对象本身、PageResult.records、数组元素，以及同模块的嵌套对象（children / parent ...） */
    public static void mask(JsonNode node, String module, Set<String> hiddenLower) {
        if (node == null || hiddenLower.isEmpty()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode e : node) {
                mask(e, module, hiddenLower);
            }
        } else if (node.isObject()) {
            ObjectNode obj = (ObjectNode)node;
            JsonNode records = obj.get("records");
            if (records != null && records.isArray() && obj.has("total")) {
                mask(records, module, hiddenLower);
                return;
            }
            List<String> drop = new ArrayList<>();
            Iterator<String> it = obj.fieldNames();
            while (it.hasNext()) {
                String k = it.next();
                if (hiddenLower.contains(k.toLowerCase(Locale.ROOT))) {
                    drop.add(k);
                }
            }
            obj.remove(drop);
            for (String nk : FieldCatalog.nestedKeys(module)) {
                JsonNode n = obj.get(nk);
                if (n != null && (n.isObject() || n.isArray())) {
                    mask(n, module, hiddenLower);
                }
            }
        }
    }

    // ───────────────────────── 写：比对 ─────────────────────────

    /**
     * @param body    请求体（对象）
     * @param current 当前值（更新时）；新建或无法取当前值时为 null，此时受限字段只要有值即拒绝
     * @param aliases 请求键 → 字段名（如状态流转的 targetBiz → statusBiz）
     */
    public static void checkWrite(String module, FieldPolicy policy, JsonNode body, JsonNode current, Map<String, String> aliases) {
        if (policy.isEmpty() || body == null || !body.isObject()) {
            return;
        }
        List<String> denied = new ArrayList<>();
        Iterator<Entry<String, JsonNode>> it = body.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            String field = aliases == null ? e.getKey() : aliases.getOrDefault(e.getKey(), e.getKey());
            if (!policy.isLocked(field)) {
                continue;
            }
            JsonNode before = current == null ? null : getIgnoreCase(current, field);
            boolean changed = current == null ? !isEmpty(e.getValue()) : !looseEquals(e.getValue(), before);
            if (changed) {
                denied.add(FieldCatalog.label(module, field));
            }
        }
        if (!denied.isEmpty()) {
            throw BusinessException.forbidden("无权修改字段：" + String.join("、", denied));
        }
    }

    static JsonNode getIgnoreCase(JsonNode obj, String key) {
        JsonNode v = obj.get(key);
        if (v != null) {
            return v;
        }
        Iterator<Entry<String, JsonNode>> it = obj.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    static boolean isEmpty(JsonNode v) {
        return v == null || v.isNull() || v.isMissingNode()
            || (v.isTextual() && v.asText().isBlank())
            || ((v.isArray() || v.isObject()) && v.isEmpty());
    }

    static boolean looseEquals(JsonNode a, JsonNode b) {
        if (isEmpty(a) && isEmpty(b)) {
            return true;
        }
        if (isEmpty(a) || isEmpty(b)) {
            return false;
        }
        BigDecimal na = num(a);
        BigDecimal nb = num(b);
        if (na != null && nb != null) {
            return na.compareTo(nb) == 0;
        }
        // 数组 vs 逗号分隔字符串（工单关联资产：请求传数组，库里存 CSV）
        if ((a.isArray() && b.isTextual()) || (a.isTextual() && b.isArray())) {
            return csvSet(a).equals(csvSet(b));
        }
        if (a.isValueNode() && b.isValueNode()) {
            String sa = a.asText().trim();
            String sb = b.asText().trim();
            // 日期时间：前端常传 "2026-01-02"，实体序列化可能是 "2026-01-02T00:00:00"
            return sa.equals(sb) || (sa.length() >= 10 && sb.length() >= 10 && (sa.startsWith(sb) || sb.startsWith(sa)) && sa.charAt(4) == '-');
        }
        return a.equals(b);
    }

    private static Set<String> csvSet(JsonNode v) {
        Set<String> out = new java.util.TreeSet<>();
        if (v.isArray()) {
            for (JsonNode e : v) {
                String s = e.isObject() && e.has("assetNo") ? e.get("assetNo").asText() : e.asText();
                if (!s.isBlank()) {
                    out.add(s.trim());
                }
            }
        } else {
            for (String s : v.asText().split("[,，;；、\\s]+")) {
                if (!s.isBlank()) {
                    out.add(s.trim());
                }
            }
        }
        return out;
    }

    private static BigDecimal num(JsonNode v) {
        if (v.isNumber()) {
            return v.decimalValue();
        }
        if (v.isTextual()) {
            String s = v.asText().trim();
            if (!s.isEmpty() && s.matches("-?\\d+(\\.\\d+)?")) {
                return new BigDecimal(s);
            }
        }
        return null;
    }

    // ───────────────────────── 导出：过滤列 ─────────────────────────

    /** 返回应保留的列下标 */
    public int[] keepColumns(LoginUser user, String module, String[] fields) {
        FieldPolicy p = this.policy(user, module);
        Set<String> hiddenLower = p.hiddenKeysLower(module);
        List<Integer> keep = new ArrayList<>();
        for (int i = 0; i < fields.length; i++) {
            if (fields[i] == null || !hiddenLower.contains(fields[i].toLowerCase(Locale.ROOT))) {
                keep.add(i);
            }
        }
        return keep.stream().mapToInt(Integer::intValue).toArray();
    }

    public static <T> List<T> pick(List<T> row, int[] keep) {
        List<T> out = new ArrayList<>(keep.length);
        for (int i : keep) {
            out.add(i < row.size() ? row.get(i) : null);
        }
        return out;
    }
}
