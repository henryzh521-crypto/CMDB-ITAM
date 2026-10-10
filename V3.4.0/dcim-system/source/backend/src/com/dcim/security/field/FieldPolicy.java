package com.dcim.security.field;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 某用户在某模块上的有效字段策略：隐藏字段（不可见、不可写）与只读字段（可见、不可写）。
 * 字段名大小写不敏感（实体经 Jackson 序列化时 uHeight 会变成 uheight）。
 */
public record FieldPolicy(Set<String> hidden, Set<String> readonly) {
    public static final FieldPolicy NONE = new FieldPolicy(Set.of(), Set.of());

    public static FieldPolicy of(Collection<?> hidden, Collection<?> readonly) {
        Set<String> h = norm(hidden);
        Set<String> r = norm(readonly);
        r.removeAll(h);
        return new FieldPolicy(Set.copyOf(h), Set.copyOf(r));
    }

    private static Set<String> norm(Collection<?> in) {
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (in != null) {
            for (Object o : in) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    out.add(String.valueOf(o).trim());
                }
            }
        }
        return out;
    }

    public boolean isEmpty() {
        return this.hidden.isEmpty() && this.readonly.isEmpty();
    }

    public boolean isHidden(String field) {
        return contains(this.hidden, field);
    }

    /** 隐藏或只读 = 当前用户不能写 */
    public boolean isLocked(String field) {
        return contains(this.hidden, field) || contains(this.readonly, field);
    }

    private static boolean contains(Set<String> set, String field) {
        if (field == null) {
            return false;
        }
        for (String s : set) {
            if (s.equalsIgnoreCase(field)) {
                return true;
            }
        }
        return false;
    }

    /** 隐藏字段及其派生键（小写），用于剔除响应 */
    public Set<String> hiddenKeysLower(String module) {
        Set<String> out = new TreeSet<>();
        for (String f : this.hidden) {
            out.add(f.toLowerCase(Locale.ROOT));
            FieldCatalog.FieldDef d = FieldCatalog.find(module, f);
            if (d != null) {
                d.extraKeys().forEach(k -> out.add(k.toLowerCase(Locale.ROOT)));
            }
        }
        return out;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hidden", List.copyOf(new TreeSet<>(this.hidden)));
        m.put("readonly", List.copyOf(new TreeSet<>(this.readonly)));
        return m;
    }
}
