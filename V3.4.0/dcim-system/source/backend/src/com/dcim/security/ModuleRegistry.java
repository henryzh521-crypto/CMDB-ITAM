package com.dcim.security;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ModuleRegistry {
    private static final List<ModuleRegistry.ModuleDef> MODULES = List.of(
        new ModuleRegistry.ModuleDef("departments", "部门", "组织人员"),
        new ModuleRegistry.ModuleDef("racks", "机柜", "基础设施"),
        new ModuleRegistry.ModuleDef("machineRooms", "机房", "基础设施"),
        new ModuleRegistry.ModuleDef("assets", "资产", "资产管理"),
        new ModuleRegistry.ModuleDef("suppliers", "供应商", "供应商管理"),
        new ModuleRegistry.ModuleDef("materials", "物料", "仓储供应"),
        new ModuleRegistry.ModuleDef("warehouses", "仓库", "仓储供应"),
        new ModuleRegistry.ModuleDef("stockLogs", "出入库", "仓储供应"),
        new ModuleRegistry.ModuleDef("workorders", "工单", "运维工单"),
        new ModuleRegistry.ModuleDef("inventories", "盘点", "仓储供应"),
        new ModuleRegistry.ModuleDef("categories", "资产分类", "资产管理"),
        new ModuleRegistry.ModuleDef("contracts", "合同管理", "供应商管理"),
        new ModuleRegistry.ModuleDef("depreciation", "折旧管理", "财务管理"),
        new ModuleRegistry.ModuleDef("people", "人员名录", "组织人员"),
        new ModuleRegistry.ModuleDef("units", "计量单位", "仓储供应"),
        new ModuleRegistry.ModuleDef("materialCats", "物料分类", "仓储供应"),
        new ModuleRegistry.ModuleDef("codeRules", "编码规则", "系统配置"),
        new ModuleRegistry.ModuleDef("system", "系统管理", "系统管理"),
        new ModuleRegistry.ModuleDef("rfid", "RFID验收", "资产管理"),
        new ModuleRegistry.ModuleDef("report", "报表中心", "报表中心"),
        new ModuleRegistry.ModuleDef("finance", "财务管理", "财务管理"),
        new ModuleRegistry.ModuleDef("discovery", "自动发现", "采集自动化"),
        new ModuleRegistry.ModuleDef("gpu", "GPU管理", "采集自动化"),
        new ModuleRegistry.ModuleDef("changes", "变更管理", "变更管理"),
        new ModuleRegistry.ModuleDef("monitoring", "监控告警", "监控告警"),
        new ModuleRegistry.ModuleDef("network", "网络资源（IP/VLAN/线缆）", "基础设施"),
        new ModuleRegistry.ModuleDef("problems", "问题管理", "运维工单"),
        new ModuleRegistry.ModuleDef("knowledge", "知识库", "运维工单")
    );

    public List<ModuleRegistry.ModuleDef> list() {
        return MODULES;
    }

    public boolean exists(String code) {
        return MODULES.stream().anyMatch(m -> m.code().equals(code));
    }

    public String nameOf(String code) {
        return MODULES.stream().filter(m -> m.code().equals(code)).map(ModuleRegistry.ModuleDef::name).findFirst().orElse(code);
    }

    public static record ModuleDef(String code, String name, String group) {
    }
}
