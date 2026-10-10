package com.dcim.common.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EventTypes {
    public static final String ASSET_CREATED = "asset.created";
    public static final String ASSET_UPDATED = "asset.updated";
    public static final String ASSET_STATUS_CHANGED = "asset.status_changed";
    public static final String ASSET_MOVED = "asset.moved";
    public static final String ASSET_DELETED = "asset.deleted";
    public static final String WORKORDER_CREATED = "workorder.created";
    public static final String WORKORDER_STATUS_CHANGED = "workorder.status_changed";
    public static final String WORKORDER_COMPLETED = "workorder.completed";
    public static final String STOCK_MOVEMENT = "stock.movement";
    public static final String STOCK_LOW = "stock.low";
    public static final String CHANGE_STATUS_CHANGED = "change.status_changed";
    public static final String ALERT_FIRING = "alert.firing";
    public static final String ALERT_RESOLVED = "alert.resolved";
    public static final String ASSET_WARRANTY_EXPIRING = "asset.warranty_expiring";
    public static final String CONTRACT_EXPIRING = "contract.expiring";
    public static final String PROBLEM_STATUS_CHANGED = "problem.status_changed";
    public static final String WORKORDER_SLA_ESCALATED = "workorder.sla_escalated";
    public static final String INVENTORY_DIFF = "inventory.diff";
    public static final String PING = "ping";
    public static final Map<String, String> CATALOG;

    private EventTypes() {
    }

    public static boolean isKnown(String type) {
        return CATALOG.containsKey(type);
    }

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("asset.created", "资产新增（含导入、验收入库）");
        m.put("asset.updated", "资产信息修改");
        m.put("asset.status_changed", "资产业务状态流转");
        m.put("asset.moved", "资产上架 / 下架 / 迁移 / 调拨");
        m.put("asset.deleted", "资产删除");
        m.put("workorder.created", "工单创建");
        m.put("workorder.status_changed", "工单状态流转（审批、处理、驳回等）");
        m.put("workorder.completed", "工单完成");
        m.put("stock.movement", "库存出入库 / 校准 / 转仓");
        m.put("stock.low", "物料库存低于安全库存");
        m.put("change.status_changed", "变更请求状态流转");
        m.put("alert.firing", "监控告警触发（维护期内的告警不推送）");
        m.put("alert.resolved", "监控告警恢复");
        m.put("asset.warranty_expiring", "资产保修即将到期（按提醒节点，默认 60/30/7 天）");
        m.put("contract.expiring", "合同即将到期（按提醒节点，默认 90/30/7 天）");
        m.put("problem.status_changed", "问题单状态流转");
        m.put("workorder.sla_escalated", "工单 SLA 超时升级（提醒处理人与上级）");
        m.put("inventory.diff", "盘点完成存在差异（盘亏 / 盘盈 / 错位 / 物料差额）");
        CATALOG = Collections.unmodifiableMap(m);
    }
}
