package com.dcim.workorder.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class CsvExport {
    private static final String[][] COLUMNS = new String[][]{
        {"woNo", "工单编号"},
        {"woType", "类型"},
        {"title", "标题"},
        {"status", "状态"},
        {"priority", "优先级"},
        {"applicant", "申请人"},
        {"handler", "处理人"},
        {"applyTime", "申请时间"},
        {"expectFinish", "期望完成"},
        {"finishTime", "完成时间"},
        {"slaResolveDeadline", "SLA解决截止"},
        {"slaOverdue", "是否超期"},
        {"firstResponseAt", "首次响应"},
        {"slaEscalations", "升级次数"},
        {"approvalLevel", "审批级别"},
        {"relatedAssets", "关联资产"},
        {"relatedMaterials", "关联物料"},
        {"rootCause", "根因"},
        {"parentWo", "父工单"},
        {"completeNote", "完成说明"}
    };

    private CsvExport() {
    }

    static String render(List<Map<String, Object>> rows) {
        return render(rows, java.util.Set.of());
    }

    /** v3.4：hiddenLower = 当前用户不可见的字段（小写），对应列不导出 */
    static String render(List<Map<String, Object>> rows, java.util.Set<String> hiddenLower) {
        List<String[]> cols = Arrays.stream(COLUMNS).filter(c -> !hiddenLower.contains(c[0].toLowerCase(java.util.Locale.ROOT))).toList();
        StringBuilder sb = new StringBuilder();
        appendRow(sb, cols.stream().map(c -> c[1]).toList());

        for (Map<String, Object> row : rows) {
            List<String> cells = new ArrayList<>(cols.size());

            for (String[] col : cols) {
                cells.add(cell(row.get(col[0])));
            }

            appendRow(sb, cells);
        }

        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }

            sb.append(escape(cells.get(i)));
        }

        sb.append("\r\n");
    }

    static String cell(Object v) {
        if (v == null) {
            return "";
        } else if (v instanceof Boolean b) {
            return b ? "是" : "否";
        } else {
            return String.valueOf(v);
        }
    }

    static String escape(String raw) {
        if (raw == null) {
            return "";
        } else {
            String v = raw;
            if (!raw.isEmpty()) {
                char c = raw.charAt(0);
                if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
                    v = "'" + raw;
                }
            }

            boolean needQuote = v.indexOf(44) >= 0 || v.indexOf(34) >= 0 || v.indexOf(10) >= 0 || v.indexOf(13) >= 0;
            return needQuote ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
        }
    }
}
