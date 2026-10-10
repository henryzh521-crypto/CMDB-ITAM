package com.dcim.collector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IreEngine {
    public static final String POLICY_FILL = "fill";
    public static final String POLICY_MANUAL = "manual";
    public static final String MATCH_SN = "SN";
    public static final String MATCH_NONE = "NONE";
    public static final String ACTION_MATCH = "MATCH";
    public static final String ACTION_PENDING = "PENDING";

    private IreEngine() {
    }

    public static String normSn(String sn) {
        return sn != null && !sn.isBlank() ? sn.trim().toUpperCase() : null;
    }

    public static IreEngine.IreResult resolve(String sn, String model, String policy, IreEngine.AssetHit hitBySn) {
        String normSn = normSn(sn);
        if (normSn != null && hitBySn != null) {
            List<Map<String, Object>> conflicts = new ArrayList<>();
            String dbModel = hitBySn.modelCode();
            if (dbModel != null && !dbModel.isBlank() && model != null && !model.isBlank() && !dbModel.trim().equalsIgnoreCase(model.trim())) {
                conflicts.add(Map.of("field", "modelCode", "expected", dbModel.trim(), "actual", model.trim()));
            }

            if (!conflicts.isEmpty() && !"fill".equals(policy)) {
                return new IreEngine.IreResult("SN", "PENDING", hitBySn.assetNo(), "SN 命中台账但硬件型号不一致，标记待人工确认", conflicts);
            } else {
                String reason = conflicts.isEmpty() ? "SN 命中台账，带外回填硬件字段" : "SN 命中台账；硬件型号与台账不一致，按策略静默采纳带外";
                return new IreEngine.IreResult("SN", "MATCH", hitBySn.assetNo(), reason, conflicts);
            }
        } else {
            return normSn == null
                ? new IreEngine.IreResult("NONE", "PENDING", null, "未读到 SN，入待认领池", List.of())
                : new IreEngine.IreResult("NONE", "PENDING", null, "SN 未命中台账，入待认领池", List.of());
        }
    }

    public static record AssetHit(String assetNo, String sn, String modelCode) {
    }

    public static record IreResult(String matchType, String action, String matchedAssetNo, String reason, List<Map<String, Object>> conflicts) {
        public Map<String, Object> toDecision() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("matchType", this.matchType);
            m.put("action", this.action);
            m.put("matchedAssetNo", this.matchedAssetNo);
            m.put("reason", this.reason);
            m.put("conflicts", this.conflicts);
            return m;
        }
    }
}
