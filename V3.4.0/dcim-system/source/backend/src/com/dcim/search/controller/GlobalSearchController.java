package com.dcim.search.controller;

import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.RbacService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/search"})
public class GlobalSearchController {
    @org.springframework.beans.factory.annotation.Autowired
    private com.dcim.security.field.FieldPolicyService fieldPolicies;
    private final JdbcTemplate jdbcTemplate;
    private final RbacService rbacService;
    private final com.dcim.security.DataScopeService dataScope;

    @GetMapping
    public ApiResult<List<Map<String, Object>>> search(@RequestParam String keyword, @RequestParam(defaultValue = "10") int limit) {
        LoginUser user = CurrentUser.get();
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return ApiResult.ok(List.of());
        } else if (kw.length() > 64) {
            throw BusinessException.badRequest("搜索关键字不超过 64 个字符");
        } else {
            int capped = Math.max(1, Math.min(limit, 50));
            String like = "%" + escapeLike(kw) + "%";
            List<Map<String, Object>> results = new ArrayList<>();
            if (this.rbacService.canRead(user, "assets")) {
                // v3.4：隐藏字段不参与匹配，也不回显（否则可用看不到的 SN / IP 反查资产）
                List<String> cols = new ArrayList<>(List.of("asset_no", "name"));
                if (!this.fieldPolicies.isHidden(user, "assets", "sn")) {
                    cols.add("sn");
                }
                if (!this.fieldPolicies.isHidden(user, "assets", "mgmtIp")) {
                    cols.add("mgmt_ip");
                }
                if (!this.fieldPolicies.isHidden(user, "assets", "bizIp")) {
                    cols.add("biz_ip");
                }
                String statusCol = this.fieldPolicies.isHidden(user, "assets", "statusBiz") ? "NULL" : "status_biz";
                List<Object> args = new ArrayList<>();
                List<String> preds = new ArrayList<>();
                for (String c : cols) {
                    preds.add(c + " ILIKE ? ESCAPE '\\'");
                    args.add(like);
                }
                String scope = this.dataScope.assetSql(user, "", args);
                args.add(like);
                args.add(capped);
                results.addAll(
                    this.jdbcTemplate
                        .queryForList(
                            "SELECT 'asset' AS type, asset_no AS id, name AS title, " + statusCol + " AS status\nFROM asset\nWHERE deleted_at IS NULL\n  AND (" + String.join(" OR ", preds) + ")"
                                + scope
                                + "\nORDER BY (asset_no ILIKE ? ESCAPE '\\') DESC, asset_no\nLIMIT ?\n",
                            args.toArray()
                        )
                );
            }

            if (this.rbacService.canRead(user, "workorders")) {
                List<Object> args = new ArrayList<>(List.of(like, like));
                String scope = this.dataScope.workorderSql(user, "", args);
                args.add(capped);
                results.addAll(
                    this.jdbcTemplate
                        .queryForList(
                            "SELECT 'workorder' AS type, wo_no AS id, title, status\nFROM workorder\nWHERE deleted_at IS NULL AND (wo_no ILIKE ? ESCAPE '\\' OR title ILIKE ? ESCAPE '\\')"
                                + scope
                                + "\nORDER BY apply_time DESC NULLS LAST\nLIMIT ?\n",
                            args.toArray()
                        )
                );
            }

            if (this.rbacService.canRead(user, "machineRooms")) {
                results.addAll(
                    this.jdbcTemplate
                        .queryForList(
                            "SELECT 'room' AS type, room_code AS id, name AS title, NULL AS status\nFROM machine_room\nWHERE room_code ILIKE ? ESCAPE '\\' OR name ILIKE ? ESCAPE '\\'\nORDER BY room_code\nLIMIT ?\n",
                            new Object[]{like, like, capped}
                        )
                );
            }

            if (this.rbacService.canRead(user, "racks")) {
                results.addAll(
                    this.jdbcTemplate
                        .queryForList(
                            "SELECT 'rack' AS type, rack_code AS id,\n       COALESCE(NULLIF(location_name, ''), rack_code) AS title, room_code AS status\nFROM rack\nWHERE rack_code ILIKE ? ESCAPE '\\' OR location_name ILIKE ? ESCAPE '\\'\nORDER BY rack_code\nLIMIT ?\n",
                            new Object[]{like, like, capped}
                        )
                );
            }

            if (this.rbacService.canRead(user, "materials")) {
                results.addAll(
                    this.jdbcTemplate
                        .queryForList(
                            "SELECT 'material' AS type, material_code AS id, name AS title,\n       '库存 ' || COALESCE(stock, 0) AS status\nFROM material\nWHERE material_code ILIKE ? ESCAPE '\\' OR name ILIKE ? ESCAPE '\\' OR model ILIKE ? ESCAPE '\\'\nORDER BY material_code\nLIMIT ?\n",
                            new Object[]{like, like, like, capped}
                        )
                );
            }

            if (this.rbacService.canRead(user, "changes")) {
                results.addAll(
                    this.jdbcTemplate
                        .queryForList(
                            "SELECT 'change' AS type, id::text AS id, cr_no || ' ' || title AS title, status\nFROM change_request\nWHERE deleted_at IS NULL AND (cr_no ILIKE ? ESCAPE '\\' OR title ILIKE ? ESCAPE '\\')\nORDER BY id DESC\nLIMIT ?\n",
                            new Object[]{like, like, capped}
                        )
                );
            }

            if (this.rbacService.canRead(user, "system")) {
                results.addAll(
                    this.jdbcTemplate
                        .queryForList(
                            "SELECT 'user' AS type, username AS id, name AS title, NULL AS status\nFROM users\nWHERE deleted_at IS NULL AND (username ILIKE ? ESCAPE '\\' OR name ILIKE ? ESCAPE '\\')\nORDER BY username\nLIMIT ?\n",
                            new Object[]{like, like, capped}
                        )
                );
            }

            return ApiResult.ok(results);
        }
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    
    public GlobalSearchController(final JdbcTemplate jdbcTemplate, final RbacService rbacService, final com.dcim.security.DataScopeService dataScope) {
        this.dataScope = dataScope;
        this.jdbcTemplate = jdbcTemplate;
        this.rbacService = rbacService;
    }
}
