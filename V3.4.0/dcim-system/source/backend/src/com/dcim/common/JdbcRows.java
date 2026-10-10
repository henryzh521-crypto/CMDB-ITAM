package com.dcim.common;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcRows {
    private JdbcRows() {
    }

    public static List<Map<String, Object>> list(JdbcTemplate jdbc, String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());

        for (Map<String, Object> r : rows) {
            out.add(convert(r));
        }

        return out;
    }

    public static Map<String, Object> one(JdbcTemplate jdbc, String sql, Object... args) {
        List<Map<String, Object>> l = list(jdbc, sql, args);
        return l.isEmpty() ? null : l.get(0);
    }

    public static Map<String, Object> convert(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>(r.size());

        for (Entry<String, Object> e : r.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Array a) {
                try {
                    v = a.getArray() instanceof Object[] oa ? new ArrayList<>(Arrays.asList(oa)) : List.of();
                } catch (SQLException var8) {
                    v = List.of();
                }
            }

            m.put(e.getKey(), v);
        }

        return m;
    }

    public static String[] textArray(List<String> in, int maxItems, int maxLen) {
        if (in == null) {
            return new String[0];
        } else {
            LinkedHashSet<String> s = new LinkedHashSet<>();

            for (String v : in) {
                if (v != null && !v.isBlank()) {
                    String t = v.trim();
                    if (t.length() > maxLen) {
                        throw BusinessException.badRequest("「" + t.substring(0, Math.min(20, t.length())) + "…」超过 " + maxLen + " 字符");
                    }

                    s.add(t);
                }
            }

            if (s.size() > maxItems) {
                throw BusinessException.badRequest("最多 " + maxItems + " 项");
            } else {
                return s.toArray(new String[0]);
            }
        }
    }
}
