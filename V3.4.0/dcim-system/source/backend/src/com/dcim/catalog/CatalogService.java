package com.dcim.catalog;

import com.dcim.common.BusinessException;
import com.dcim.common.JdbcRows;
import com.dcim.security.LoginUser;
import com.dcim.workorder.entity.WorkorderEntity;
import com.dcim.workorder.service.WoAssetLineInput;
import com.dcim.workorder.service.WorkorderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private static final Set<String> FIELD_TYPES = Set.of("text", "textarea", "number", "select", "date", "asset", "assets", "rack");
    private final JdbcTemplate jdbc;
    private final WorkorderService workorders;
    private final ObjectMapper om;
    private static final String COLS = "id, code, name, category, description, icon, wo_type AS \"woType\", priority, form_schema::text AS \"formSchema\",\nvisible_roles AS \"visibleRoles\", sla_hint AS \"slaHint\", enabled, sort, updated_at AS \"updatedAt\"\n";

    public CatalogService(JdbcTemplate jdbc, WorkorderService workorders, ObjectMapper om) {
        this.jdbc = jdbc;
        this.workorders = workorders;
        this.om = om;
    }

    public List<Map<String, Object>> items(LoginUser user, boolean all) {
        List<Map<String, Object>> rows = JdbcRows.list(
            this.jdbc,
            "SELECT id, code, name, category, description, icon, wo_type AS \"woType\", priority, form_schema::text AS \"formSchema\",\nvisible_roles AS \"visibleRoles\", sla_hint AS \"slaHint\", enabled, sort, updated_at AS \"updatedAt\"\n FROM catalog_item ORDER BY sort, id"
        );
        List<Map<String, Object>> out = new ArrayList<>();

        for (Map<String, Object> r : rows) {
            r.put("formSchema", this.parseSchema((String)r.get("formSchema")));
            if (all && user.isAdmin()) {
                out.add(r);
            } else if (Boolean.TRUE.equals(r.get("enabled")) && visible(r, user)) {
                out.add(r);
            }
        }

        return out;
    }

    public Map<String, Object> item(long id, LoginUser user) {
        Map<String, Object> r = JdbcRows.one(
            this.jdbc,
            "SELECT id, code, name, category, description, icon, wo_type AS \"woType\", priority, form_schema::text AS \"formSchema\",\nvisible_roles AS \"visibleRoles\", sla_hint AS \"slaHint\", enabled, sort, updated_at AS \"updatedAt\"\n FROM catalog_item WHERE id = ?",
            id
        );
        if (r != null && (user.isAdmin() || Boolean.TRUE.equals(r.get("enabled")) && visible(r, user))) {
            r.put("formSchema", this.parseSchema((String)r.get("formSchema")));
            return r;
        } else {
            throw BusinessException.notFound("服务不存在或不可用");
        }
    }

    @Transactional
    public Map<String, Object> request(long itemId, Map<String, Object> values, LoginUser user) {
        Map<String, Object> item = this.item(itemId, user);
        List<Map<String, Object>> schema = (List<Map<String, Object>>)item.get("formSchema");
        Map<String, Object> v = values == null ? Map.of() : values;
        List<WoAssetLineInput> lines = new ArrayList<>();
        String targetRack = null;
        LocalDate expect = null;
        StringBuilder desc = new StringBuilder();
        desc.append("服务目录请求：").append(item.get("name")).append("（").append(item.get("code")).append("）\n");
        desc.append("申请人：").append(user.name() == null ? user.username() : user.name()).append("\n\n");
        Map<String, Object> clean = new LinkedHashMap<>();
        List<String> assetNos = new ArrayList<>();

        for (Map<String, Object> f : schema) {
            String key;
            String label;
            String type;
            boolean required;
            Object raw;
            boolean var10000;
            label201: {
                key = (String)f.get("key");
                label = (String)f.getOrDefault("label", key);
                type = (String)f.get("type");
                required = Boolean.TRUE.equals(f.get("required"));
                raw = v.get(key);
                label170:
                if (raw != null) {
                    if (raw instanceof String s && s.isBlank() || raw instanceof List<?> l && l.isEmpty()) {
                        break label170;
                    }

                    var10000 = false;
                    break label201;
                }

                var10000 = true;
            }

            boolean empty = var10000;
            if (empty) {
                if (required) {
                    throw BusinessException.badRequest("请填写「" + label + "」");
                }
            } else {
                switch (type) {
                    case "assets":
                    case "asset":
                        List<String> nos = new ArrayList<>();
                        if (raw instanceof List<?> l) {
                            l.forEach(x -> nos.add(String.valueOf(x).trim()));
                        } else {
                            nos.add(String.valueOf(raw).trim());
                        }

                        if ("asset".equals(type) && nos.size() > 1) {
                            throw BusinessException.badRequest("「" + label + "」只能选一台设备");
                        }

                        if (nos.size() > 200) {
                            throw BusinessException.badRequest("「" + label + "」一次最多 200 台");
                        }

                        for (String no : nos) {
                            if (Boolean.FALSE
                                .equals(
                                    this.jdbc
                                        .queryForObject(
                                            "SELECT EXISTS (SELECT 1 FROM asset WHERE asset_no = ? AND deleted_at IS NULL)", Boolean.class, new Object[]{no}
                                        )
                                )) {
                                throw BusinessException.badRequest("设备不存在：" + no);
                            }

                            if (!assetNos.contains(no)) {
                                assetNos.add(no);
                                WoAssetLineInput line = new WoAssetLineInput();
                                line.setAssetNo(no);
                                lines.add(line);
                            }
                        }

                        clean.put(key, nos);
                        desc.append(label).append("：").append(String.join("、", nos)).append('\n');
                        break;
                    case "rack":
                        String rack = String.valueOf(raw).trim();
                        if (Boolean.FALSE
                            .equals(this.jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM rack WHERE rack_code = ?)", Boolean.class, new Object[]{rack}))) {
                            throw BusinessException.badRequest("机柜不存在：" + rack);
                        }

                        if (targetRack == null) {
                            targetRack = rack;
                        }

                        clean.put(key, rack);
                        desc.append(label).append("：").append(rack).append('\n');
                        break;
                    case "date":
                        LocalDate d;
                        try {
                            d = LocalDate.parse(String.valueOf(raw).trim().substring(0, Math.min(10, String.valueOf(raw).trim().length())));
                        } catch (DateTimeParseException var30) {
                            throw BusinessException.badRequest("「" + label + "」日期格式不正确");
                        }

                        if ("expectFinish".equals(key)) {
                            if (d.isBefore(LocalDate.now())) {
                                throw BusinessException.badRequest("期望完成日期不能早于今天");
                            }

                            expect = d;
                        }

                        clean.put(key, d.toString());
                        desc.append(label).append("：").append(d).append('\n');
                        break;
                    case "number":
                        double n;
                        try {
                            n = Double.parseDouble(String.valueOf(raw));
                        } catch (NumberFormatException var29) {
                            throw BusinessException.badRequest("「" + label + "」须为数字");
                        }

                        clean.put(key, n);
                        desc.append(label).append("：").append(String.valueOf(raw)).append('\n');
                        break;
                    case "select": {
                        String s = String.valueOf(raw);
                        if (f.get("options") instanceof List<?> ol && !ol.contains(s)) {
                            throw BusinessException.badRequest("「" + label + "」选项无效：" + s);
                        }

                        clean.put(key, s);
                        desc.append(label).append("：").append(s).append('\n');
                        break;
                    }
                    default:
                        String s = String.valueOf(raw);
                        int max = f.get("maxLength") instanceof Number mx ? mx.intValue() : ("textarea".equals(type) ? 4000 : 500);
                        if (s.length() > max) {
                            throw BusinessException.badRequest("「" + label + "」不超过 " + max + " 字");
                        }

                        clean.put(key, s);
                        desc.append(label).append("：").append("textarea".equals(type) ? "\n" + s : s).append('\n');
                }
            }
        }

        String title = "【服务请求】" + item.get("name");
        if (!assetNos.isEmpty()) {
            title = title + " " + assetNos.get(0) + (assetNos.size() > 1 ? " 等 " + assetNos.size() + " 台" : "");
        }

        if (title.length() > 200) {
            title = title.substring(0, 200);
        }

        WorkorderEntity wo = this.workorders
            .create(
                (String)item.get("woType"),
                title,
                desc.toString(),
                (String)item.get("priority"),
                lines.isEmpty() ? null : lines,
                null,
                null,
                null,
                targetRack,
                null,
                expect,
                null,
                null,
                null,
                null,
                null
            );

        String json;
        try {
            json = this.om.writeValueAsString(clean);
        } catch (JsonProcessingException var28) {
            json = "{}";
        }

        this.jdbc
            .update(
                "INSERT INTO catalog_request (item_id, wo_no, requester, form_values) VALUES (?, ?, ?, ?::jsonb)",
                new Object[]{itemId, wo.getWoNo(), user.username(), json}
            );
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("woNo", wo.getWoNo());
        out.put("status", wo.getStatus());
        return out;
    }

    public Map<String, Object> myRequests(String username, long page, long size) {
        long sz = Math.max(1L, Math.min(size, 100L));
        Long total = (Long)this.jdbc.queryForObject("SELECT COUNT(*) FROM catalog_request WHERE requester = ?", Long.class, new Object[]{username});
        List<Map<String, Object>> rows = this.jdbc
            .queryForList(
                "SELECT r.id, r.wo_no AS \"woNo\", r.created_at AS \"createdAt\", i.name AS \"itemName\", i.icon,\n       w.status, w.title, w.handler, w.priority, w.finish_time AS \"finishTime\",\n       w.sla_resolve_deadline AS \"slaDeadline\"\nFROM catalog_request r JOIN catalog_item i ON i.id = r.item_id\nLEFT JOIN workorder w ON w.wo_no = r.wo_no\nWHERE r.requester = ? ORDER BY r.id DESC LIMIT ? OFFSET ?\n",
                new Object[]{username, sz, (Math.max(page, 1L) - 1L) * sz}
            );
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("records", rows);
        return out;
    }

    @Transactional
    public long save(Long id, CatalogService.ItemReq r) {
        if (r.code() == null || !r.code().matches("[A-Za-z0-9_\\-]{1,50}")) {
            throw BusinessException.badRequest("编码须为 1-50 位字母、数字、- 或 _");
        } else if (r.name() != null && !r.name().isBlank() && r.name().length() <= 100) {
            if (Boolean.FALSE
                .equals(this.jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM workflow_config WHERE wo_type = ?)", Boolean.class, new Object[]{r.woType()}))) {
                throw BusinessException.badRequest("工单类型不存在：" + r.woType());
            } else {
                String prio = r.priority() != null && !r.priority().isBlank() ? r.priority() : "中";
                if (!Set.of("高", "中", "低").contains(prio)) {
                    throw BusinessException.badRequest("优先级须为 高 / 中 / 低");
                } else {
                    String schema = this.validateSchema(r.formSchema());
                    String[] roles = JdbcRows.textArray(r.visibleRoles(), 50, 50);

                    try {
                        if (id == null) {
                            Long newId = (Long)this.jdbc
                                .queryForObject(
                                    "INSERT INTO catalog_item (code, name, category, description, icon, wo_type, priority, form_schema,\n                          visible_roles, sla_hint, enabled, sort)\nVALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?) RETURNING id\n",
                                    Long.class,
                                    new Object[]{
                                        r.code().trim(),
                                        r.name().trim(),
                                        cat(r.category()),
                                        r.description(),
                                        r.icon(),
                                        r.woType(),
                                        prio,
                                        schema,
                                        roles,
                                        r.slaHint(),
                                        r.enabled() == null || r.enabled(),
                                        r.sort() == null ? 0 : r.sort()
                                    }
                                );
                            return newId == null ? 0L : newId;
                        } else if (this.jdbc
                                .update(
                                    "UPDATE catalog_item SET code = ?, name = ?, category = ?, description = ?, icon = ?, wo_type = ?,\n       priority = ?, form_schema = ?::jsonb, visible_roles = ?, sla_hint = ?, enabled = ?, sort = ?,\n       updated_at = NOW() WHERE id = ?\n",
                                    new Object[]{
                                        r.code().trim(),
                                        r.name().trim(),
                                        cat(r.category()),
                                        r.description(),
                                        r.icon(),
                                        r.woType(),
                                        prio,
                                        schema,
                                        roles,
                                        r.slaHint(),
                                        r.enabled() == null || r.enabled(),
                                        r.sort() == null ? 0 : r.sort(),
                                        id
                                    }
                                )
                            == 0) {
                            throw BusinessException.notFound("服务不存在");
                        } else {
                            return id;
                        }
                    } catch (DuplicateKeyException var7) {
                        throw BusinessException.conflict("服务编码已存在：" + r.code());
                    }
                }
            }
        } else {
            throw BusinessException.badRequest("请填写服务名称");
        }
    }

    public void delete(long id) {
        Long used = (Long)this.jdbc.queryForObject("SELECT COUNT(*) FROM catalog_request WHERE item_id = ?", Long.class, new Object[]{id});
        if (used != null && used > 0L) {
            this.jdbc.update("UPDATE catalog_item SET enabled = FALSE, updated_at = NOW() WHERE id = ?", new Object[]{id});
        } else {
            this.jdbc.update("DELETE FROM catalog_item WHERE id = ?", new Object[]{id});
        }
    }

    public List<String> woTypes() {
        return this.jdbc.queryForList("SELECT wo_type FROM workflow_config ORDER BY id", String.class);
    }

    private String validateSchema(List<Map<String, Object>> schema) {
        if (schema == null) {
            return "[]";
        } else if (schema.size() > 30) {
            throw BusinessException.badRequest("表单字段不超过 30 个");
        } else {
            Set<String> keys = new HashSet<>();
            List<Map<String, Object>> out = new ArrayList<>();

            for (Map<String, Object> f : schema) {
                String key = f.get("key") == null ? null : String.valueOf(f.get("key"));
                String type = f.get("type") == null ? null : String.valueOf(f.get("type"));
                String label = f.get("label") == null ? null : String.valueOf(f.get("label"));
                if (key != null && key.matches("[A-Za-z][A-Za-z0-9_]{0,39}")) {
                    if (!keys.add(key)) {
                        throw BusinessException.badRequest("字段 key 重复：" + key);
                    }

                    if (type != null && FIELD_TYPES.contains(type)) {
                        if (label != null && !label.isBlank() && label.length() <= 50) {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("key", key);
                            m.put("label", label.trim());
                            m.put("type", type);
                            m.put("required", Boolean.TRUE.equals(f.get("required")));
                            if ("select".equals(type)) {
                                if (!(f.get("options") instanceof List<?> ol) || ol.isEmpty() || ol.size() > 50) {
                                    throw BusinessException.badRequest("下拉字段 " + label + " 须有 1-50 个选项");
                                }

                                List<String> opts = new ArrayList<>();
                                ol.forEach(o -> opts.add(String.valueOf(o)));
                                m.put("options", opts);
                            }

                            if (f.get("placeholder") != null) {
                                m.put("placeholder", String.valueOf(f.get("placeholder")));
                            }

                            if (f.get("maxLength") instanceof Number n) {
                                m.put("maxLength", Math.max(1, Math.min(n.intValue(), 10000)));
                            }

                            out.add(m);
                            continue;
                        }

                        throw BusinessException.badRequest("字段 " + key + " 须有标签（不超过 50 字）");
                    }

                    throw BusinessException.badRequest("字段类型无效：" + type + "（可选 " + String.join(" / ", FIELD_TYPES) + "）");
                }

                throw BusinessException.badRequest("字段 key 须以字母开头，仅含字母数字下划线：" + key);
            }

            try {
                return this.om.writeValueAsString(out);
            } catch (JsonProcessingException var12) {
                throw BusinessException.badRequest("表单定义无效");
            }
        }
    }

    private List<Map<String, Object>> parseSchema(String json) {
        try {
            return (List<Map<String, Object>>)this.om
                .readValue(json == null ? "[]" : json, this.om.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (JsonProcessingException var3) {
            return List.of();
        }
    }

    private static boolean visible(Map<String, Object> item, LoginUser user) {
        if (user.isAdmin()) {
            return true;
        } else {
            if (item.get("visibleRoles") instanceof List<?> l && !l.isEmpty()) {
                for (Object r : l) {
                    if (user.roles() != null && user.roles().contains(String.valueOf(r))) {
                        return true;
                    }
                }

                return false;
            }

            return true;
        }
    }

    private static String cat(String c) {
        return c != null && !c.isBlank() ? c.trim() : "通用服务";
    }

    public static record ItemReq(
        String code,
        String name,
        String category,
        String description,
        String icon,
        String woType,
        String priority,
        List<Map<String, Object>> formSchema,
        List<String> visibleRoles,
        String slaHint,
        Boolean enabled,
        Integer sort
    ) {
    }
}
