package com.dcim.workorder.sla;

import java.time.LocalDateTime;
import java.util.List;

public final class SlaPolicy {
    public static final List<String> SLA_EXCLUDED_STATES = List.of("已完成", "已关闭", "已驳回");

    private SlaPolicy() {
    }

    public static boolean isExcluded(String status) {
        return status != null && SLA_EXCLUDED_STATES.contains(status);
    }

    public static boolean isOverdue(LocalDateTime deadline, String status, LocalDateTime now) {
        return deadline != null && now != null && !isExcluded(status) && deadline.isBefore(now);
    }

    public static boolean isResponded(LocalDateTime firstResponseAt) {
        return firstResponseAt != null;
    }

    public static List<String> sqlExcludedLiterals() {
        return SLA_EXCLUDED_STATES;
    }
}
