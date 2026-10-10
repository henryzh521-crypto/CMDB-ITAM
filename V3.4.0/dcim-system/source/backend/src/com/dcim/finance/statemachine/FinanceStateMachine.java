package com.dcim.finance.statemachine;

import com.dcim.common.BusinessException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FinanceStateMachine {
    private static final Map<String, List<String>> TRANSITIONS = buildTransitions();
    private static final Map<String, Set<String>> ACTION_FROM = buildActionFrom();
    public static final String ACT_CAPITALIZE = "capitalize";
    public static final String ACT_IMPAIR = "impair";
    public static final String ACT_DEPRECIATE = "depreciate";
    public static final String ACT_DISPOSE = "dispose";
    public static final String ACT_WRITE_OFF = "writeOff";
    public static final String ACT_BUYOUT = "buyout";

    private FinanceStateMachine() {
    }

    private static Map<String, List<String>> buildTransitions() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("未入账", List.of("已入账", "使用权资产确认"));
        m.put("已入账", List.of("转固计提"));
        m.put("转固计提", List.of("减值", "折旧完毕"));
        m.put("减值", List.of("减值", "折旧完毕"));
        m.put("折旧完毕", List.of("处置中"));
        m.put("处置中", List.of("核销"));
        m.put("核销", List.of());
        m.put("使用权资产确认", List.of("折旧+利息"));
        m.put("折旧+利息", List.of("留购", "折旧完毕", "处置中"));
        m.put("留购", List.of());
        return Collections.unmodifiableMap(m);
    }

    private static Map<String, Set<String>> buildActionFrom() {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        m.put("capitalize", Set.of("已入账", "使用权资产确认"));
        m.put("impair", Set.of("转固计提", "减值"));
        m.put("depreciate", Set.of("转固计提", "减值", "折旧+利息"));
        m.put("dispose", Set.of("折旧完毕", "折旧+利息"));
        m.put("writeOff", Set.of("处置中"));
        m.put("buyout", Set.of("折旧+利息"));
        return Collections.unmodifiableMap(m);
    }

    public static boolean canTransit(String from, String to) {
        if (from != null && to != null) {
            List<String> targets = TRANSITIONS.get(from);
            return targets != null && targets.contains(to);
        } else {
            return false;
        }
    }

    public static void assertTransit(String from, String to) {
        if (!canTransit(from, to)) {
            if (from == null || unknownState(from)) {
                throw BusinessException.badRequest("未知状态：" + from);
            } else if (to == null || unknownState(to)) {
                throw BusinessException.badRequest("未知状态：" + to);
            } else if (TRANSITIONS.get(from).isEmpty()) {
                throw BusinessException.badRequest("财务状态「" + from + "」为终态，不允许再流转到「" + to + "」");
            } else {
                throw BusinessException.badRequest("财务状态不允许从「" + from + "」变更为「" + to + "」，请按财务状态机流程操作");
            }
        }
    }

    public static void assertActionAllowed(String action, String from) {
        Set<String> allowed = ACTION_FROM.get(action);
        if (allowed == null) {
            throw BusinessException.badRequest("未知财务动作：" + action);
        } else if (from == null || !allowed.contains(from)) {
            throw BusinessException.badRequest("财务状态「" + from + "」不允许执行该操作（允许的状态：" + String.join("/", allowed) + "）");
        }
    }

    public static List<String> transitionsOf(String state) {
        if (state == null) {
            return List.of();
        } else {
            List<String> targets = TRANSITIONS.get(state);
            return targets == null ? List.of() : targets;
        }
    }

    public static List<String> finStates() {
        return List.copyOf(TRANSITIONS.keySet());
    }

    public static Set<String> depreciableStates() {
        return ACTION_FROM.get("depreciate");
    }

    private static boolean unknownState(String state) {
        return !TRANSITIONS.containsKey(state);
    }
}
