package com.dcim.security;

import com.dcim.common.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RbacService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, String> ACTION_LABELS = Map.of(
        "create", "新增", "update", "编辑", "delete", "删除", "import", "导入", "export", "导出", "approve", "审批"
    );
    private final ModuleRegistry moduleRegistry;

    public RbacService(ModuleRegistry moduleRegistry) {
        this.moduleRegistry = moduleRegistry;
    }

    public boolean isAdmin(LoginUser user) {
        return user != null && user.isAdmin();
    }

    public boolean canRead(LoginUser user, String module) {
        if (user == null) {
            return false;
        } else if (user.isAdmin()) {
            return true;
        } else {
            LoginUser.ModulePerm p = user.modulePerms().get(module);
            return p != null && (p.read() || p.write());
        }
    }

    public boolean canWrite(LoginUser user, String module, String action) {
        if (user == null) {
            return false;
        } else if (user.isAdmin()) {
            return true;
        } else if ("delete".equals(action)) {
            return this.canDelete(user, module);
        } else {
            LoginUser.ModulePerm p = user.modulePerms().get(module);
            return p != null && p.write();
        }
    }

    /**
     * v3.4：删除需显式授予——模块写权限 + 角色操作权限里 delete=true。
     * v3.3 及以前非管理员一律不能删除，角色页上的「删除」勾选不起作用。
     */
    public boolean canDelete(LoginUser user, String module) {
        if (user == null) {
            return false;
        } else if (user.isAdmin()) {
            return true;
        }
        LoginUser.ModulePerm p = user.modulePerms().get(module);
        Map<String, Boolean> acts = user.actionPerms() == null ? null : user.actionPerms().get(module);
        return p != null && p.write() && acts != null && Boolean.TRUE.equals(acts.get("delete"));
    }

    public String moduleName(String module) {
        return this.moduleRegistry.nameOf(module);
    }

    public void assertAction(LoginUser user, String module, String action) {
        if (user != null && !user.isAdmin()) {
            // 修复：旧缓存反序列化的 actionPerms 可能为 null，先判空（与 actionCan 等调用点一致）
            Map<String, Boolean> cfg = user.actionPerms() == null ? null : user.actionPerms().get(module);
            if (cfg != null) {
                if (Boolean.FALSE.equals(cfg.get(action))) {
                    String moduleName = this.moduleRegistry.nameOf(module);
                    String actionName = ACTION_LABELS.getOrDefault(action, action);
                    throw new BusinessException(403, "当前角色的「" + moduleName + "」模块已禁用「" + actionName + "」操作，请通过工单办理");
                }
            }
        }
    }

    public Map<String, List<String>> fieldPolicy(LoginUser user, String module) {
        List<String> hidden = new ArrayList<>();
        List<String> readonly = new ArrayList<>();
        if (user != null && !user.isAdmin()) {
            // 修复：旧缓存反序列化的 fieldPerms 可能为 null，先判空
            Map<String, Object> cfg = user.fieldPerms() == null ? null : user.fieldPerms().get(module);
            if (cfg != null) {
                hidden.addAll(this.strings(cfg.get("hidden")));
                readonly.addAll(this.strings(cfg.get("readonly")));
            }
        }

        return Map.of("hidden", List.copyOf(hidden), "readonly", List.copyOf(readonly));
    }

    public String findLockedFieldWrite(Map<String, Object> body, Map<String, List<String>> policy, Map<String, Object> prev) {
        if (body != null && policy != null) {
            List<String> locked = new ArrayList<>(policy.getOrDefault("hidden", List.of()));
            locked.addAll(policy.getOrDefault("readonly", List.of()));

            for (String field : body.keySet()) {
                if (locked.contains(field) && (prev != null ? !this.valuesEqual(body.get(field), prev.get(field)) : !this.valueEmpty(body.get(field)))) {
                    return field;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private boolean valueEmpty(Object v) {
        return v == null || "".equals(v);
    }

    private boolean valuesEqual(Object a, Object b) {
        if (this.valueEmpty(a) && this.valueEmpty(b)) {
            return true;
        } else if (a == null || b == null) {
            return false;
        } else if (a.equals(b)) {
            return true;
        } else {
            try {
                return MAPPER.writeValueAsString(a).equals(MAPPER.writeValueAsString(b)) || MAPPER.valueToTree(a).equals(MAPPER.valueToTree(b));
            } catch (JsonProcessingException var4) {
                return false;
            }
        }
    }

    private List<String> strings(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            list.forEach(x -> out.add(String.valueOf(x)));
        }

        return out;
    }
}
