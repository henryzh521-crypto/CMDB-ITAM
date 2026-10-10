package com.dcim.quality;

import com.dcim.common.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DataQualityService {
    private static final String IN_USE = "a.status_biz IN ('在用(计费)', '维修中', '停用中')";
    public static final List<DataQualityService.Rule> RULES = List.of(
        new DataQualityService.Rule("no_sn", "缺少序列号", "完整性", "SN 是盘点、维保报修与采集对账的主键", 3, "NULLIF(btrim(a.sn), '') IS NULL", "TRUE"),
        new DataQualityService.Rule("no_model", "缺少型号", "完整性", "影响功率估算、故障批次分析与备件匹配", 2, "NULLIF(btrim(a.model_code), '') IS NULL", "TRUE"),
        new DataQualityService.Rule("no_category", "未分类", "完整性", "未分类资产不纳入监控模板与报表统计", 3, "a.category_code IS NULL", "TRUE"),
        new DataQualityService.Rule(
            "no_location", "在用但无机柜位置", "完整性", "在用 / 维修 / 停用资产应有机柜位置", 3, "a.rack_code IS NULL", "a.status_biz IN ('在用(计费)', '维修中', '停用中')"
        ),
        new DataQualityService.Rule(
            "no_unit", "有机柜无 U 位", "完整性", "已登记机柜但未登记 U 位，机柜视图与容量统计不准", 2, "a.rack_code IS NOT NULL AND a.unit_no IS NULL", "a.rack_code IS NOT NULL"
        ),
        new DataQualityService.Rule(
            "no_ip",
            "在用服务器无 IP",
            "完整性",
            "无 IP 的服务器无法纳入监控与 IP 管理",
            2,
            "NULLIF(btrim(a.biz_ip), '') IS NULL AND NULLIF(btrim(a.mgmt_ip), '') IS NULL",
            "a.status_biz IN ('在用(计费)', '维修中', '停用中') AND c.path ~ '^/(SRV|GPU|STO)/'"
        ),
        new DataQualityService.Rule(
            "bad_ip",
            "IP 格式错误",
            "准确性",
            "IP 字段无法解析为合法地址",
            2,
            "((NULLIF(btrim(a.biz_ip), '') IS NOT NULL AND try_inet(a.biz_ip) IS NULL) OR (NULLIF(btrim(a.mgmt_ip), '') IS NOT NULL AND try_inet(a.mgmt_ip) IS NULL))",
            "TRUE"
        ),
        new DataQualityService.Rule(
            "dup_ip",
            "IP 冲突",
            "准确性",
            "同一 IP 登记在多台资产上",
            3,
            "(try_inet(a.biz_ip) IN (SELECT ip FROM dupip) OR try_inet(a.mgmt_ip) IN (SELECT ip FROM dupip))",
            "TRUE"
        ),
        new DataQualityService.Rule(
            "room_mismatch",
            "机房与机柜不一致",
            "一致性",
            "资产登记的机房与其机柜所在机房不同（未填机房时以机柜为准，不计入）",
            2,
            "r.rack_code IS NOT NULL AND a.room_code IS NOT NULL AND a.room_code <> r.room_code",
            "a.rack_code IS NOT NULL"
        ),
        new DataQualityService.Rule(
            "unknown_rack", "机柜不存在", "一致性", "资产引用了不存在的机柜编码", 3, "a.rack_code IS NOT NULL AND r.rack_code IS NULL", "a.rack_code IS NOT NULL"
        ),
        new DataQualityService.Rule("no_warranty", "在用但无保修日期", "完整性", "无法做过保提醒与维保续约规划", 1, "a.warranty_end IS NULL", "a.status_biz IN ('在用(计费)', '维修中', '停用中')"),
        new DataQualityService.Rule(
            "no_purchase", "自有资产缺采购信息", "完整性", "缺采购日期或金额，影响折旧与成本分摊", 1, "(a.purchase_date IS NULL OR a.purchase_price IS NULL)", "a.owner_type = 'SELF'"
        ),
        new DataQualityService.Rule(
            "no_dept", "在用但无使用部门", "完整性", "成本分摊与责任归属依赖使用部门", 1, "NULLIF(btrim(a.department), '') IS NULL", "a.status_biz IN ('在用(计费)', '维修中', '停用中')"
        ),
        new DataQualityService.Rule("no_power", "已上架但无功率数据", "完整性", "资产、型号、分类均无功率，机柜功率容量偏低", 1, "p.power_w IS NULL", "a.rack_code IS NOT NULL")
    );
    private static final String WITH = "WITH dupip AS (\n  SELECT ip FROM (\n    SELECT try_inet(biz_ip) AS ip, asset_no FROM asset WHERE deleted_at IS NULL\n    UNION ALL SELECT try_inet(mgmt_ip), asset_no FROM asset WHERE deleted_at IS NULL) x\n  WHERE ip IS NOT NULL GROUP BY ip HAVING COUNT(DISTINCT asset_no) > 1)\n";
    private static final String FROM = " FROM asset a\n LEFT JOIN category c ON c.category_code = a.category_code\n LEFT JOIN rack r ON r.rack_code = a.rack_code\n LEFT JOIN v_asset_power p ON p.asset_no = a.asset_no\n WHERE a.deleted_at IS NULL AND a.status_biz <> '报废处置'\n";
    private final JdbcTemplate jdbc;

    public DataQualityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> summary() {
        StringBuilder sql = new StringBuilder(
            "WITH dupip AS (\n  SELECT ip FROM (\n    SELECT try_inet(biz_ip) AS ip, asset_no FROM asset WHERE deleted_at IS NULL\n    UNION ALL SELECT try_inet(mgmt_ip), asset_no FROM asset WHERE deleted_at IS NULL) x\n  WHERE ip IS NOT NULL GROUP BY ip HAVING COUNT(DISTINCT asset_no) > 1)\nSELECT COUNT(*) AS total"
        );

        for (DataQualityService.Rule r : RULES) {
            sql.append(", COUNT(*) FILTER (WHERE (").append(r.scope()).append(")) AS \"s_").append(r.id()).append('"');
            sql.append(", COUNT(*) FILTER (WHERE (").append(r.scope()).append(") AND (").append(r.where()).append(")) AS \"f_").append(r.id()).append('"');
        }

        sql.append(
            " FROM asset a\n LEFT JOIN category c ON c.category_code = a.category_code\n LEFT JOIN rack r ON r.rack_code = a.rack_code\n LEFT JOIN v_asset_power p ON p.asset_no = a.asset_no\n WHERE a.deleted_at IS NULL AND a.status_biz <> '报废处置'\n"
        );
        Map<String, Object> row = this.jdbc.queryForMap(sql.toString());
        long total = ((Number)row.get("total")).longValue();
        List<Map<String, Object>> rules = new ArrayList<>();
        double weighted = 0.0;
        double weights = 0.0;
        Map<String, double[]> dims = new LinkedHashMap<>();

        for (DataQualityService.Rule r : RULES) {
            long scope = ((Number)row.get("s_" + r.id())).longValue();
            long fail = ((Number)row.get("f_" + r.id())).longValue();
            double pass = scope == 0L ? 100.0 : (double)(scope - fail) * 100.0 / (double)scope;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id());
            m.put("name", r.name());
            m.put("dimension", r.dimension());
            m.put("description", r.description());
            m.put("weight", r.weight());
            m.put("scope", scope);
            m.put("failed", fail);
            m.put("passRate", round(pass));
            rules.add(m);
            if (scope > 0L) {
                weighted += pass * (double)r.weight();
                weights += (double)r.weight();
                double[] d = dims.computeIfAbsent(r.dimension(), k -> new double[2]);
                d[0] += pass * (double)r.weight();
                d[1] += (double)r.weight();
            }
        }

        Map<String, Object> dimOut = new LinkedHashMap<>();
        dims.forEach((k, v) -> dimOut.put(k, round(v[0] / v[1])));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("score", weights == 0.0 ? 100.0 : round(weighted / weights));
        out.put("dimensions", dimOut);
        out.put("rules", rules);
        out.put(
            "byDepartment",
            this.jdbc
                .queryForList(
                    "WITH dupip AS (\n  SELECT ip FROM (\n    SELECT try_inet(biz_ip) AS ip, asset_no FROM asset WHERE deleted_at IS NULL\n    UNION ALL SELECT try_inet(mgmt_ip), asset_no FROM asset WHERE deleted_at IS NULL) x\n  WHERE ip IS NOT NULL GROUP BY ip HAVING COUNT(DISTINCT asset_no) > 1)\nSELECT COALESCE(NULLIF(btrim(a.department), ''), '（未填）') AS department, COUNT(*) AS total, COUNT(*) FILTER (WHERE "
                        + anyFail()
                        + ") AS problems FROM asset a\n LEFT JOIN category c ON c.category_code = a.category_code\n LEFT JOIN rack r ON r.rack_code = a.rack_code\n LEFT JOIN v_asset_power p ON p.asset_no = a.asset_no\n WHERE a.deleted_at IS NULL AND a.status_biz <> '报废处置'\n GROUP BY 1 ORDER BY problems DESC LIMIT 10"
                )
        );
        return out;
    }

    public Map<String, Object> failed(String ruleId, long page, long size) {
        DataQualityService.Rule r = RULES.stream()
            .filter(x -> x.id().equals(ruleId))
            .findFirst()
            .orElseThrow(() -> BusinessException.notFound("规则不存在：" + ruleId));
        String cond = " AND (" + r.scope() + ") AND (" + r.where() + ")";
        Long total = (Long)this.jdbc
            .queryForObject(
                "WITH dupip AS (\n  SELECT ip FROM (\n    SELECT try_inet(biz_ip) AS ip, asset_no FROM asset WHERE deleted_at IS NULL\n    UNION ALL SELECT try_inet(mgmt_ip), asset_no FROM asset WHERE deleted_at IS NULL) x\n  WHERE ip IS NOT NULL GROUP BY ip HAVING COUNT(DISTINCT asset_no) > 1)\nSELECT COUNT(*) FROM asset a\n LEFT JOIN category c ON c.category_code = a.category_code\n LEFT JOIN rack r ON r.rack_code = a.rack_code\n LEFT JOIN v_asset_power p ON p.asset_no = a.asset_no\n WHERE a.deleted_at IS NULL AND a.status_biz <> '报废处置'\n"
                    + cond,
                Long.class
            );
        long sz = Math.max(1L, Math.min(size, 1000L));
        List<Map<String, Object>> rows = this.jdbc
            .queryForList(
                "WITH dupip AS (\n  SELECT ip FROM (\n    SELECT try_inet(biz_ip) AS ip, asset_no FROM asset WHERE deleted_at IS NULL\n    UNION ALL SELECT try_inet(mgmt_ip), asset_no FROM asset WHERE deleted_at IS NULL) x\n  WHERE ip IS NOT NULL GROUP BY ip HAVING COUNT(DISTINCT asset_no) > 1)\nSELECT a.asset_no AS \"assetNo\", a.name, a.sn, a.model_code AS \"modelCode\", a.category_code AS \"categoryCode\",\n       a.status_biz AS \"statusBiz\", a.room_code AS \"roomCode\", a.rack_code AS \"rackCode\", a.unit_no AS \"unitNo\",\n       a.biz_ip AS \"bizIp\", a.mgmt_ip AS \"mgmtIp\", a.department, a.warranty_end AS \"warrantyEnd\"\n FROM asset a\n LEFT JOIN category c ON c.category_code = a.category_code\n LEFT JOIN rack r ON r.rack_code = a.rack_code\n LEFT JOIN v_asset_power p ON p.asset_no = a.asset_no\n WHERE a.deleted_at IS NULL AND a.status_biz <> '报废处置'\n"
                    + cond
                    + " ORDER BY a.asset_no LIMIT ? OFFSET ?",
                new Object[]{sz, (Math.max(page, 1L) - 1L) * sz}
            );
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rule", Map.of("id", r.id(), "name", r.name(), "description", r.description()));
        out.put("total", total);
        out.put("records", rows);
        return out;
    }

    private static String anyFail() {
        StringBuilder sb = new StringBuilder("FALSE");

        for (DataQualityService.Rule r : RULES) {
            if (r.weight() >= 2) {
                sb.append(" OR ((").append(r.scope()).append(") AND (").append(r.where()).append("))");
            }
        }

        return sb.toString();
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public static record Rule(String id, String name, String dimension, String description, int weight, String where, String scope) {
    }
}
