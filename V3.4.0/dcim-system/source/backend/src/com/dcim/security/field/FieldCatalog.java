package com.dcim.security.field;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 可配置字段权限的字段目录（v3.4）。
 *
 * <p>只收录「业务上可能需要对部分角色隐藏 / 只读」的字段；编号、名称、状态时间戳等身份类字段不可配置，
 * 否则列表失去可读性。{@link FieldDef#extraKeys()} 是同一信息在响应里的派生键，隐藏时一并剔除
 * （例如隐藏「关联资产」时，工单详情里的 items 也要去掉）。
 */
public final class FieldCatalog {
    public record FieldDef(String field, String label, List<String> extraKeys) {
        public FieldDef(String field, String label) {
            this(field, label, List.of());
        }
    }

    public static final String ASSETS = "assets";
    public static final String WORKORDERS = "workorders";

    private static final Map<String, List<FieldDef>> CATALOG = new LinkedHashMap<>();
    /** 响应里嵌套着同模块对象的键（剔除时递归进入） */
    private static final Map<String, Set<String>> NESTED = Map.of(
        ASSETS, Set.of("children", "parent", "before", "after", "snapshot"),
        WORKORDERS, Set.of("childWos")
    );

    static {
        CATALOG.put(ASSETS, List.of(
            new FieldDef("sn", "序列号"),
            new FieldDef("categoryCode", "分类"),
            new FieldDef("modelCode", "型号"),
            new FieldDef("ownerType", "归属类型"),
            new FieldDef("statusBiz", "业务状态"),
            new FieldDef("statusFin", "财务状态"),
            new FieldDef("regionCode", "区域"),
            new FieldDef("roomCode", "机房"),
            new FieldDef("rackCode", "机柜"),
            new FieldDef("unitNo", "U 位"),
            new FieldDef("uHeight", "高度（U）"),
            new FieldDef("department", "使用部门"),
            new FieldDef("owner", "责任人"),
            new FieldDef("mgmtIp", "管理 IP"),
            new FieldDef("bizIp", "业务 IP"),
            new FieldDef("purchaseDate", "采购日期"),
            new FieldDef("purchasePrice", "采购价格"),
            new FieldDef("warrantyEnd", "维保到期"),
            new FieldDef("rackDate", "上架日期"),
            new FieldDef("contractNo", "合同号"),
            new FieldDef("ratedPowerW", "额定功率（W）"),
            new FieldDef("parentAssetNo", "父资产", List.of("parent")),
            new FieldDef("attrs", "扩展属性"),
            new FieldDef("finance", "财务信息（原值、净值、折旧）")
        ));
        CATALOG.put(WORKORDERS, List.of(
            new FieldDef("description", "描述"),
            new FieldDef("priority", "优先级"),
            new FieldDef("expectFinish", "期望完成"),
            new FieldDef("applicant", "申请人", List.of("applicantUsername")),
            new FieldDef("handler", "处理人"),
            new FieldDef("relatedAssets", "关联资产", List.of("items")),
            new FieldDef("relatedMaterials", "关联物料", List.of("details")),
            new FieldDef("targetLocation", "目标位置", List.of("targetRack", "targetUnit")),
            new FieldDef("rootCause", "根因"),
            new FieldDef("process", "处理过程"),
            new FieldDef("completeNote", "完成说明"),
            new FieldDef("acceptNote", "验收说明", List.of("acceptResult", "acceptor", "acceptTime")),
            new FieldDef("costLabor", "人工费用"),
            new FieldDef("costService", "服务费用"),
            new FieldDef("costOther", "其他费用"),
            new FieldDef("attachments", "附件")
        ));
    }

    private FieldCatalog() {
    }

    public static Map<String, List<FieldDef>> all() {
        return CATALOG;
    }

    public static boolean supports(String module) {
        return CATALOG.containsKey(module);
    }

    public static List<FieldDef> of(String module) {
        return CATALOG.getOrDefault(module, List.of());
    }

    public static FieldDef find(String module, String field) {
        for (FieldDef d : of(module)) {
            if (d.field().equalsIgnoreCase(field)) {
                return d;
            }
        }
        return null;
    }

    public static String label(String module, String field) {
        FieldDef d = find(module, field);
        return d == null ? field : d.label();
    }

    public static String moduleLabel(String module) {
        return ASSETS.equals(module) ? "资产" : WORKORDERS.equals(module) ? "工单" : module;
    }

    public static Set<String> nestedKeys(String module) {
        return NESTED.getOrDefault(module, Set.of());
    }
}
