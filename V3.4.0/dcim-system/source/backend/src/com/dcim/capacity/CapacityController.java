package com.dcim.capacity;

import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/capacity"})
public class CapacityController {
    private static final Set<String> SETTING_KEYS = Set.of(
        "power_warn_pct", "power_max_pct", "power_statuses", "warranty_remind_days", "contract_remind_days", "remind_roles", "electricity_price"
    );
    private final CapacityService capacity;
    private final JdbcTemplate jdbc;

    @GetMapping({"/rooms"})
    @RequirePerm(
        module = "racks"
    )
    public ApiResult<Map<String, Object>> rooms() {
        // v3.4：机房容量是全量聚合（10 万资产约 120 ms），且与用户无关；30 秒内复用结果（ADR-09）
        Object[] c = this.roomsCache;
        long now = System.currentTimeMillis();
        if (c != null && now - (Long)c[0] < ROOMS_CACHE_MS) {
            return ApiResult.ok((Map<String, Object>)c[1]);
        }
        Map<String, Object> fresh = this.computeRooms();
        this.roomsCache = new Object[]{now, fresh};
        return ApiResult.ok(fresh);
    }

    private static final long ROOMS_CACHE_MS = 30_000L;
    private volatile Object[] roomsCache;

    private Map<String, Object> computeRooms() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rooms", this.capacity.rooms());
        out.put("warnPct", this.capacity.warnPct());
        out.put("maxPct", this.capacity.maxPct());
        out.put(
            "assetsWithoutPower", this.jdbc.queryForObject("SELECT COUNT(*) FROM v_asset_power WHERE rack_code IS NOT NULL AND power_w IS NULL", Long.class)
        );
        out.put("cachedAt", java.time.LocalDateTime.now().withNano(0).toString());
        return out;
    }

    @GetMapping({"/racks"})
    @RequirePerm(
        module = "racks"
    )
    public ApiResult<List<Map<String, Object>>> racks(@RequestParam(required = false) String roomCode) {
        return ApiResult.ok(this.capacity.racks(roomCode));
    }

    @GetMapping({"/racks/{rackCode}/assets"})
    @RequirePerm(
        module = "racks"
    )
    public ApiResult<List<Map<String, Object>>> rackAssets(@PathVariable String rackCode) {
        return ApiResult.ok(
            this.jdbc
                .queryForList(
                    "SELECT a.asset_no AS \"assetNo\", a.name, a.model_code AS \"modelCode\", a.status_biz AS \"statusBiz\",\n       a.unit_no AS \"unitNo\", a.u_height AS \"uHeight\", a.rated_power_w AS \"ratedPowerW\",\n       p.power_w AS \"powerW\", p.power_source AS \"powerSource\"\nFROM asset a JOIN v_asset_power p ON p.asset_no = a.asset_no\nWHERE a.rack_code = ? AND a.deleted_at IS NULL ORDER BY a.unit_no DESC NULLS LAST\n",
                    new Object[]{rackCode}
                )
        );
    }

    @GetMapping({"/check"})
    @RequirePerm(
        module = "racks"
    )
    public ApiResult<Map<String, Object>> check(
        @RequestParam String rackCode,
        @RequestParam(defaultValue = "1") int uHeight,
        @RequestParam(required = false) Integer powerW,
        @RequestParam(required = false) String excludeAssetNo
    ) {
        return ApiResult.ok(this.capacity.check(rackCode, uHeight, powerW, excludeAssetNo));
    }

    @GetMapping({"/recommend"})
    @RequirePerm(
        module = "racks"
    )
    public ApiResult<List<Map<String, Object>>> recommend(
        @RequestParam(required = false) String roomCode,
        @RequestParam(defaultValue = "1") int uHeight,
        @RequestParam(required = false) Integer powerW,
        @RequestParam(defaultValue = "1") int count,
        @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResult.ok(this.capacity.recommend(roomCode, uHeight, powerW, count, Math.max(1, Math.min(limit, 100))));
    }

    @GetMapping({"/power-profiles"})
    @RequirePerm(
        module = "racks"
    )
    public ApiResult<List<Map<String, Object>>> profiles() {
        return ApiResult.ok(
            this.jdbc
                .queryForList(
                    "SELECT p.id, p.match_type AS \"matchType\", p.match_value AS \"matchValue\", p.rated_w AS \"ratedW\",\n       p.remark,\n       CASE WHEN p.match_type = 'MODEL'\n            THEN (SELECT COUNT(*) FROM asset a WHERE a.deleted_at IS NULL AND a.model_code = p.match_value)\n            ELSE (SELECT COUNT(*) FROM asset a JOIN category c ON c.category_code = a.category_code\n                  JOIN category pc ON pc.category_code = p.match_value\n                  WHERE a.deleted_at IS NULL AND c.path LIKE pc.path || '%') END AS \"assetCount\"\nFROM power_profile p ORDER BY p.match_type DESC, p.match_value\n"
                )
        );
    }

    @GetMapping({"/power-profiles/missing-models"})
    @RequirePerm(
        module = "racks"
    )
    public ApiResult<List<Map<String, Object>>> missingModels() {
        return ApiResult.ok(
            this.jdbc
                .queryForList(
                    "SELECT a.model_code AS \"modelCode\", COUNT(*) AS \"assetCount\"\nFROM v_asset_power v JOIN asset a ON a.asset_no = v.asset_no\nWHERE v.power_w IS NULL AND a.model_code IS NOT NULL AND a.model_code <> ''\nGROUP BY a.model_code ORDER BY COUNT(*) DESC LIMIT 50\n"
                )
        );
    }

    @PostMapping({"/power-profiles"})
    @RequirePerm(
        module = "racks",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "racks",
        objType = "power_profile",
        objIdSpEL = "#req.matchValue()"
    )
    public ApiResult<Map<String, Object>> createProfile(@RequestBody CapacityController.ProfileReq req) {
        this.validate(req);

        try {
            this.jdbc
                .update(
                    "INSERT INTO power_profile (match_type, match_value, rated_w, remark) VALUES (?, ?, ?, ?)",
                    new Object[]{req.matchType(), req.matchValue().trim(), req.ratedW(), req.remark()}
                );
        } catch (DuplicateKeyException var3) {
            throw BusinessException.conflict("该" + ("MODEL".equals(req.matchType()) ? "型号" : "分类") + "已有功率模板");
        }

        return ApiResult.ok(Map.of("ok", true));
    }

    @PutMapping({"/power-profiles/{id}"})
    @RequirePerm(
        module = "racks",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "racks",
        objType = "power_profile",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> updateProfile(@PathVariable Long id, @RequestBody CapacityController.ProfileReq req) {
        this.validate(req);

        int n;
        try {
            n = this.jdbc
                .update(
                    "UPDATE power_profile SET match_type = ?, match_value = ?, rated_w = ?, remark = ?, updated_at = NOW() WHERE id = ?",
                    new Object[]{req.matchType(), req.matchValue().trim(), req.ratedW(), req.remark(), id}
                );
        } catch (DuplicateKeyException var5) {
            throw BusinessException.conflict("该型号/分类已有功率模板");
        }

        if (n == 0) {
            throw BusinessException.notFound("功率模板不存在");
        } else {
            return ApiResult.ok(Map.of("ok", true));
        }
    }

    @DeleteMapping({"/power-profiles/{id}"})
    @RequirePerm(
        module = "racks",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "racks",
        objType = "power_profile",
        objIdSpEL = "#id"
    )
    public ApiResult<Void> deleteProfile(@PathVariable Long id) {
        this.jdbc.update("DELETE FROM power_profile WHERE id = ?", new Object[]{id});
        return ApiResult.ok();
    }

    private void validate(CapacityController.ProfileReq r) {
        if (r == null || !Set.of("MODEL", "CATEGORY").contains(r.matchType())) {
            throw BusinessException.badRequest("匹配方式须为 MODEL（型号）或 CATEGORY（分类）");
        } else if (r.matchValue() == null || r.matchValue().isBlank() || r.matchValue().length() > 80) {
            throw BusinessException.badRequest("请填写型号/分类编码（不超过 80 字符）");
        } else if (r.ratedW() != null && r.ratedW() >= 0 && r.ratedW() <= 200000) {
            if ("CATEGORY".equals(r.matchType())
                && Boolean.FALSE
                    .equals(
                        this.jdbc
                            .queryForObject(
                                "SELECT EXISTS (SELECT 1 FROM category WHERE category_code = ?)", Boolean.class, new Object[]{r.matchValue().trim()}
                            )
                    )) {
                throw BusinessException.badRequest("分类不存在：" + r.matchValue());
            }
        } else {
            throw BusinessException.badRequest("功率须在 0-200000 W");
        }
    }

    @GetMapping({"/settings"})
    @RequirePerm(
        module = "racks"
    )
    public ApiResult<List<Map<String, Object>>> settings() {
        return ApiResult.ok(this.jdbc.queryForList("SELECT setting_key AS key, setting_value AS value, remark FROM capacity_setting ORDER BY setting_key"));
    }

    @PutMapping({"/settings"})
    @RequirePerm(
        module = "racks",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "racks",
        objType = "capacity_setting"
    )
    @Transactional
    public ApiResult<Void> putSettings(@RequestBody Map<String, String> values) {
        LoginUser u = CurrentUser.get();
        if (u != null && u.isAdmin()) {
            for (Entry<String, String> e : values.entrySet()) {
                String k = e.getKey();
                String v = e.getValue() == null ? "" : e.getValue().trim();
                if (!SETTING_KEYS.contains(k)) {
                    throw BusinessException.badRequest("未知参数：" + k);
                }

                switch (k) {
                    case "power_warn_pct":
                    case "power_max_pct":
                        if (!v.matches("\\d{1,3}") || Integer.parseInt(v) < 1 || Integer.parseInt(v) > 100) {
                            throw BusinessException.badRequest("百分比须为 1-100 的整数");
                        }
                        break;
                    case "warranty_remind_days":
                    case "contract_remind_days":
                        if (!v.matches("\\d{1,3}(\\s*,\\s*\\d{1,3}){0,9}")) {
                            throw BusinessException.badRequest("提醒节点须为逗号分隔的天数，如 60,30,7");
                        }
                        break;
                    case "electricity_price":
                        if (!v.isEmpty() && !v.matches("\\d{1,3}(\\.\\d{1,4})?")) {
                            throw BusinessException.badRequest("电价须为数字（元/kWh），如 0.65；留空表示不计电费");
                        }
                        break;
                    case "power_statuses":
                    case "remind_roles":
                        if (v.isEmpty() || v.length() > 200) {
                            throw BusinessException.badRequest(k + " 不能为空且不超过 200 字符");
                        }
                }

                this.jdbc.update("UPDATE capacity_setting SET setting_value = ?, updated_at = NOW() WHERE setting_key = ?", new Object[]{v, k});
            }

            int warn = this.capacity.warnPct();
            int max = this.capacity.maxPct();
            if (warn > max) {
                throw BusinessException.badRequest("预警阈值不能高于上限");
            } else {
                return ApiResult.ok();
            }
        } else {
            throw BusinessException.forbidden("仅管理员可修改容量参数");
        }
    }

    
    public CapacityController(final CapacityService capacity, final JdbcTemplate jdbc) {
        this.capacity = capacity;
        this.jdbc = jdbc;
    }

    public static record ProfileReq(String matchType, String matchValue, Integer ratedW, String remark) {
    }
}
