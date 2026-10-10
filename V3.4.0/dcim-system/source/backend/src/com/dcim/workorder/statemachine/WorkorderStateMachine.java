package com.dcim.workorder.statemachine;

import com.dcim.common.BusinessException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class WorkorderStateMachine {
    public static final String ACT_SUBMIT = "提交审批";
    public static final String ACT_APPROVE = "审批通过";
    public static final String ST_PENDING = "待处理";
    public static final String ST_APPROVING = "待审批";
    public static final String ST_PROCESSING = "处理中";
    public static final String ST_DONE = "已完成";
    public static final String ST_REJECTED = "已驳回";
    public static final String ST_CLOSED = "已关闭";
    private static final Map<String, Map<String, List<String>>> TRANSITIONS = buildTransitions();

    private WorkorderStateMachine() {
    }

    private static Map<String, Map<String, List<String>>> buildTransitions() {
        Map<String, Map<String, List<String>>> m = new LinkedHashMap<>();
        Map<String, List<String>> pending = new LinkedHashMap<>();
        pending.put("开始处理", List.of("处理中"));
        pending.put("提交审批", List.of("待审批"));
        // v3.5：补充「待处理→已关闭」撤销路径（仅申请人可撤销，权限在 WorkorderService.transition 校验）
        pending.put("撤销", List.of("已关闭"));
        m.put("待处理", pending);
        Map<String, List<String>> approving = new LinkedHashMap<>();
        approving.put("审批通过", List.of("待审批", "处理中"));
        approving.put("驳回", List.of("已驳回"));
        m.put("待审批", approving);
        Map<String, List<String>> rejected = new LinkedHashMap<>();
        rejected.put("重新提交", List.of("待审批"));
        rejected.put("关闭", List.of("已关闭"));
        m.put("已驳回", rejected);
        Map<String, List<String>> processing = new LinkedHashMap<>();
        processing.put("完成工单", List.of("已完成"));
        processing.put("退回", List.of("待处理"));
        processing.put("关闭", List.of("已关闭"));
        m.put("处理中", processing);
        m.put("已完成", new LinkedHashMap<>(Map.of("重新打开", List.of("处理中"))));
        m.put("已关闭", new LinkedHashMap<>());
        return Collections.unmodifiableMap(m);
    }

    public static boolean isKnownState(String state) {
        return state != null && TRANSITIONS.containsKey(state);
    }

    public static List<String> states() {
        return List.copyOf(TRANSITIONS.keySet());
    }

    public static List<String> actionsOf(String from, boolean needApproval) {
        Map<String, List<String>> actions = TRANSITIONS.get(from);
        if (actions == null) {
            return List.of();
        } else {
            List<String> out = new ArrayList<>();

            for (Entry<String, List<String>> e : actions.entrySet()) {
                if ((!"提交审批".equals(e.getKey()) || needApproval) && !e.getValue().isEmpty()) {
                    out.add(e.getKey());
                }
            }

            return List.copyOf(out);
        }
    }

    public static List<String> targetsOf(String from, String action, boolean needApproval) {
        if ("提交审批".equals(action) && !needApproval) {
            return List.of();
        } else {
            Map<String, List<String>> actions = TRANSITIONS.get(from);
            if (actions == null) {
                return List.of();
            } else {
                List<String> targets = actions.get(action);
                return targets == null ? List.of() : targets;
            }
        }
    }

    public static boolean canAct(String from, String action, boolean needApproval) {
        return !targetsOf(from, action, needApproval).isEmpty();
    }

    public static boolean canTransit(String from, String to, boolean needApproval) {
        if (from != null && to != null) {
            for (String action : actionsOf(from, needApproval)) {
                if (targetsOf(from, action, needApproval).contains(to)) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    public static void assertTransit(String from, String to, boolean needApproval) {
        if (!canTransit(from, to, needApproval)) {
            List<String> actions = actionsOf(from, needApproval);
            if (!isKnownState(from)) {
                throw BusinessException.conflict("未知工单状态：" + from);
            } else if (actions.isEmpty()) {
                throw BusinessException.conflict("工单当前状态「" + from + "」为终态，无可用操作");
            } else {
                throw BusinessException.conflict("工单状态不允许从「" + from + "」变更为「" + to + "」，当前可用操作：" + String.join("、", actions));
            }
        }
    }
}
