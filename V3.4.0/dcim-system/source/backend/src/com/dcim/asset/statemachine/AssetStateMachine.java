package com.dcim.asset.statemachine;

import com.dcim.common.BusinessException;
import com.dcim.finance.statemachine.FinanceStateMachine;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AssetStateMachine {
    private static final Map<String, List<String>> BIZ_TRANSITIONS = buildBizTransitions();

    private AssetStateMachine() {
    }

    private static Map<String, List<String>> buildBizTransitions() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("待上架", List.of("在用(计费)", "已退租"));
        m.put("在用(计费)", List.of("维修中", "停用中", "待退役"));
        m.put("维修中", List.of("在用(计费)", "停用中", "待退役"));
        m.put("停用中", List.of("在用(计费)", "待退役"));
        m.put("待退役", List.of("已退役"));
        m.put("已退役", List.of("已报废", "已退租"));
        m.put("已报废", List.of());
        m.put("已退租", List.of());
        return Collections.unmodifiableMap(m);
    }

    private static List<String> finTargets(String state) {
        return FinanceStateMachine.transitionsOf(state);
    }

    public static boolean canTransit(String from, String to) {
        if (from == null || to == null) {
            return false;
        } else {
            return BIZ_TRANSITIONS.containsKey(from) ? BIZ_TRANSITIONS.get(from).contains(to) : FinanceStateMachine.canTransit(from, to);
        }
    }

    public static void assertTransit(String from, String to) {
        if (!canTransit(from, to)) {
            if (from == null || unknownState(from)) {
                throw BusinessException.badRequest("未知状态：" + from);
            } else if (to == null || unknownState(to)) {
                throw BusinessException.badRequest("未知状态：" + to);
            } else if (transitionsOf(from).isEmpty()) {
                throw BusinessException.badRequest("状态「" + from + "」为终态，不允许再流转到「" + to + "」");
            } else {
                throw BusinessException.badRequest("状态不允许从「" + from + "」变更为「" + to + "」，请按状态机流程操作");
            }
        }
    }

    public static List<String> transitionsOf(String state) {
        if (state == null) {
            return List.of();
        } else if (BIZ_TRANSITIONS.containsKey(state)) {
            return BIZ_TRANSITIONS.get(state);
        } else {
            List<String> targets = finTargets(state);
            return targets == null ? List.of() : targets;
        }
    }

    public static List<String> bizStates() {
        return List.copyOf(BIZ_TRANSITIONS.keySet());
    }

    public static List<String> finStates() {
        return FinanceStateMachine.finStates();
    }

    private static boolean unknownState(String state) {
        return !BIZ_TRANSITIONS.containsKey(state) && !finKnown(state);
    }

    private static boolean finKnown(String state) {
        return FinanceStateMachine.finStates().contains(state);
    }
}
