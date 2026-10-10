package com.dcim.system.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.asset.statemachine.AssetStateMachine;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.asset.AssetCodeRule;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import com.dcim.system.entity.DictEntity;
import com.dcim.system.mapper.DictMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
@RequestMapping({"/api/dicts"})
public class DictController {
    private static final String BIZ_STATUS = "asset_status_biz";
    private static final String FIN_STATUS = "asset_status_fin";
    private static final String CODE_RULE = "code_rule";
    private final DictMapper dictMapper;
    private final JdbcTemplate jdbc;
    private static final Set<String> SOFT_DELETE_TABLES = Set.of("asset", "workorder", "roles", "users", "change_request", "webhook_endpoint");

    @GetMapping
    @RequirePerm(
        module = "system"
    )
    public ApiResult<List<Map<String, Object>>> list(@RequestParam(required = false) String dictType) {
        List<DictEntity> rows = this.dictMapper
            .selectList(
                (((Wrappers.<DictEntity>lambdaQuery()
                                .eq(dictType != null && !dictType.isBlank(), DictEntity::getDictType, dictType))
                            .orderByAsc(DictEntity::getDictType))
                        .orderByAsc(DictEntity::getSort))
                    .orderByAsc(DictEntity::getId)
            );
        List<Map<String, Object>> out = new ArrayList<>(rows.size());

        for (DictEntity d : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("dictType", d.getDictType());
            m.put("dictKey", d.getDictKey());
            m.put("dictValue", d.getDictValue());
            m.put("sort", d.getSort());
            m.put("status", d.getStatus());
            m.put("systemManaged", this.isSystemState(d.getDictType(), d.getDictKey()));
            out.add(m);
        }

        return ApiResult.ok(out);
    }

    @GetMapping({"/options"})
    public ApiResult<List<Map<String, Object>>> options(@RequestParam String dictType) {
        String type = dictType == null ? "" : dictType.trim();
        if (!type.isEmpty() && type.length() <= 50) {
            List<DictEntity> rows = this.dictMapper
                .selectList(
                    (((Wrappers.<DictEntity>lambdaQuery().eq(DictEntity::getDictType, type))
                                .eq(DictEntity::getStatus, 1))
                            .orderByAsc(DictEntity::getSort))
                        .orderByAsc(DictEntity::getId)
                );
            List<Map<String, Object>> out = new ArrayList<>(rows.size());

            for (DictEntity d : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("dictKey", d.getDictKey());
                m.put("dictValue", d.getDictValue());
                out.add(m);
            }

            return ApiResult.ok(out);
        } else {
            throw BusinessException.badRequest("字典类型不能为空且最长 50 字符");
        }
    }

    @PostMapping
    @RequirePerm(
        module = "system",
        write = true
    )
    @Audit(
        action = "CREATE",
        module = "system",
        objType = "dict",
        objIdSpEL = "#req.dictType() + ':' + #req.dictKey()"
    )
    @Transactional
    public ApiResult<Void> create(@RequestBody @Valid DictController.DictReq req) {
        String type = req.dictType().trim();
        String key = req.dictKey().trim();
        this.assertRuleValid(type, key, req.dictValue().trim());
        if (this.select(type, key) != null) {
            throw BusinessException.conflict("字典项已存在：" + type + " / " + key);
        } else {
            DictEntity e = new DictEntity();
            e.setDictType(type);
            e.setDictKey(key);
            e.setDictValue(req.dictValue().trim());
            e.setSort(req.sort() == null ? 0 : req.sort());
            e.setStatus(req.status() == null ? 1 : (req.status() == 0 ? 0 : 1));
            this.dictMapper.insert(e);
            return ApiResult.ok();
        }
    }

    @PutMapping({"/{id}"})
    @RequirePerm(
        module = "system",
        write = true
    )
    @Audit(
        action = "UPDATE",
        module = "system",
        objType = "dict",
        objIdSpEL = "#req.dictType() + ':' + #req.dictKey()"
    )
    @Transactional
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody @Valid DictController.DictReq req) {
        DictEntity e = (DictEntity)this.dictMapper.selectById(id);
        if (e == null) {
            throw BusinessException.notFound("字典项不存在：" + id);
        } else if (e.getDictType().equals(req.dictType().trim()) && e.getDictKey().equals(req.dictKey().trim())) {
            this.assertRuleValid(e.getDictType(), e.getDictKey(), req.dictValue().trim());
            boolean disabling = req.status() != null && req.status() == 0 && (e.getStatus() == null || e.getStatus() != 0);
            if (disabling) {
                this.assertUnreferenced(e.getDictType(), e.getDictKey(), "停用");
            }

            e.setDictValue(req.dictValue().trim());
            if (req.sort() != null) {
                e.setSort(req.sort());
            }

            if (req.status() != null) {
                e.setStatus(req.status() == 0 ? 0 : 1);
            }

            this.dictMapper.updateById(e);
            return ApiResult.ok();
        } else {
            throw BusinessException.badRequest("字典类型与字典键不可修改（键被业务数据引用，改键会孤儿化存量数据）");
        }
    }

    @DeleteMapping({"/{id}"})
    @RequirePerm(
        module = "system",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "system",
        objType = "dict",
        objIdSpEL = "#id"
    )
    @Transactional
    public ApiResult<Void> delete(@PathVariable Long id) {
        DictEntity e = (DictEntity)this.dictMapper.selectById(id);
        if (e == null) {
            throw BusinessException.notFound("字典项不存在：" + id);
        } else if (this.isSystemState(e.getDictType(), e.getDictKey())) {
            throw BusinessException.conflict("系统状态「" + e.getDictKey() + "」由状态机迁移矩阵管理，不可删除（可将其停用）");
        } else {
            this.assertUnreferenced(e.getDictType(), e.getDictKey(), "删除");
            this.dictMapper.deleteById(id);
            return ApiResult.ok();
        }
    }

    private DictEntity select(String type, String key) {
        return (DictEntity)this.dictMapper
            .selectOne((Wrappers.<DictEntity>lambdaQuery().eq(DictEntity::getDictType, type)).eq(DictEntity::getDictKey, key));
    }

    private boolean isSystemState(String dictType, String dictKey) {
        if ("asset_status_biz".equals(dictType)) {
            return AssetStateMachine.bizStates().contains(dictKey);
        } else {
            return "asset_status_fin".equals(dictType) ? AssetStateMachine.finStates().contains(dictKey) : false;
        }
    }

    private void assertUnreferenced(String dictType, String dictKey, String action) {
        if ("code_rule".equals(dictType)) {
            throw BusinessException.conflict("编码规则「" + dictKey + "」由资产编号生成直接读取，不可" + action + "；如需调整请编辑其模板内容");
        } else {
            int refs = this.referenceCount(dictType, dictKey);
            if (refs > 0) {
                throw BusinessException.conflict("字典项「" + dictKey + "」已被业务数据引用（" + refs + " 处），不可" + action + "；如需从下拉隐藏请先清理引用，或保留启用");
            }
        }
    }

    private void assertRuleValid(String dictType, String dictKey, String dictValue) {
        if ("code_rule".equals(dictType)) {
            if (!"asset".equals(dictKey)) {
                throw BusinessException.badRequest("编码规则全局仅支持 dictKey=asset（资产编号）一条，当前为：" + dictKey);
            } else {
                try {
                    AssetCodeRule.parse(dictValue);
                } catch (IllegalArgumentException var5) {
                    throw BusinessException.badRequest("资产编码规则非法：" + var5.getMessage());
                }
            }
        }
    }

    private int referenceCount(String dictType, String dictKey) {
        return switch (dictType) {
            case "region" -> this.count("asset", "region_code", dictKey)
            + this.count("machine_room", "region_code", dictKey)
            + this.count("discover_task", "region_code", dictKey)
            + this.count("workorder_item", "src_region", dictKey)
            + this.count("workorder_item", "dst_region", dictKey);
            case "material_cat" -> this.count("material", "category", dictKey);
            case "unit" -> this.count("material", "unit", dictKey);
            case "warehouse_type" -> this.count("warehouse", "warehouse_type", dictKey);
            case "asset_status_biz" -> this.count("asset", "status_biz", dictKey);
            case "asset_status_fin" -> this.count("asset", "status_fin", dictKey);
            case "wo_root_cause" -> this.count("workorder", "root_cause", dictKey);
            default -> 0;
        };
    }

    private int count(String table, String column, String value) {
        String where = SOFT_DELETE_TABLES.contains(table) ? "deleted_at IS NULL AND " : "";
        Integer n = (Integer)this.jdbc
            .queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where + column + " = ?", Integer.class, new Object[]{value});
        return n == null ? 0 : n;
    }

    
    public DictController(final DictMapper dictMapper, final JdbcTemplate jdbc) {
        this.dictMapper = dictMapper;
        this.jdbc = jdbc;
    }

    public static record DictReq(
        @NotBlank(message = "字典类型不能为空") @Size(max = 50,message = "字典类型最长 50 字符") String dictType,
        @NotBlank(message = "字典键不能为空") @Size(max = 100,message = "字典键最长 100 字符") String dictKey,
        @NotBlank(message = "字典值/名称不能为空") @Size(max = 200,message = "字典值最长 200 字符") String dictValue,
        Integer sort,
        Integer status
    ) {
    }
}
