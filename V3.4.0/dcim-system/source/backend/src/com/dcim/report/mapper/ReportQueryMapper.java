package com.dcim.report.mapper;

import com.dcim.report.dto.CategoryStatusRow;
import com.dcim.report.dto.GroupCount;
import com.dcim.report.dto.GroupCountValue;
import com.dcim.report.dto.MaterialConsumptionRow;
import com.dcim.report.dto.MaterialStockRow;
import com.dcim.report.dto.RackUsageRow;
import com.dcim.report.dto.RoomUsageRow;
import com.dcim.report.dto.SlaRow;
import com.dcim.report.dto.WarehouseStockAggRow;
import com.dcim.report.dto.WoConsumptionRow;
import com.dcim.report.dto.WoConsumptionSummaryRow;
import com.dcim.report.dto.WoCostRow;
import com.dcim.report.dto.WoCostSummaryRow;
import com.dcim.report.dto.WoDurationRow;
import com.dcim.report.dto.WoStatsRow;
import com.dcim.report.dto.WoTrendRow;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReportQueryMapper {
    String REPAIR_WO_TYPES = "wo_type IN ('故障维修', '部件更换')";

    @Select({"SELECT COUNT(*) FROM asset WHERE deleted_at IS NULL"})
    Long countAsset();

    @Select({"SELECT status_biz AS key, COUNT(*) AS count FROM asset WHERE deleted_at IS NULL GROUP BY status_biz ORDER BY 1"})
    List<GroupCount> groupAssetByStatusBiz();

    @Select({"SELECT COALESCE(NULLIF(TRIM(region_code), ''), '未分配') AS key, COUNT(*) AS count FROM asset WHERE deleted_at IS NULL GROUP BY 1 ORDER BY 1"})
    List<GroupCount> groupAssetByRegion();

    @Select({"SELECT COALESCE(NULLIF(TRIM(category_code), ''), '未分类') AS key, COUNT(*) AS count FROM asset WHERE deleted_at IS NULL GROUP BY 1 ORDER BY 1"})
    List<GroupCount> groupAssetByCategory();

    @Select({"SELECT COALESCE((SELECT SUM(u_capacity) FROM rack), 0) AS total_units, (SELECT COUNT(*) FROM rack_unit WHERE status = '占用') AS used_units"})
    RackUsageRow selectRackUsage();

    @Select({"SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE status = '待处理') AS pending, COUNT(*) FILTER (WHERE status = '待审批') AS approving, COUNT(*) FILTER (WHERE status = '处理中') AS processing, COUNT(*) FILTER (WHERE status = '已完成') AS done, COUNT(*) FILTER (WHERE status = '已驳回') AS rejected, COUNT(*) FILTER (WHERE status = '已关闭') AS closed, COUNT(*) FILTER (WHERE sla_resolve_deadline IS NOT NULL AND sla_resolve_deadline < now() AND status NOT IN ('已完成', '已关闭', '已驳回')) AS sla_overdue FROM workorder WHERE deleted_at IS NULL"})
    WoStatsRow selectWoStats();

    @Select({"SELECT COALESCE(NULLIF(TRIM(category_code), ''), '未分类') AS category, status_biz AS status_biz, COUNT(*) AS count FROM asset WHERE deleted_at IS NULL GROUP BY 1, 2 ORDER BY 1, 2"})
    List<CategoryStatusRow> selectCategoryStatusCounts();

    @Select({"WITH cap AS (\n  SELECT COALESCE(NULLIF(TRIM(room_code), ''), '未分配') AS rk, SUM(u_capacity) AS u_cap\n  FROM rack GROUP BY 1\n), occ AS (\n  SELECT COALESCE(NULLIF(TRIM(r.room_code), ''), '未分配') AS rk, COUNT(*) AS u_occ\n  FROM rack_unit ru JOIN rack r ON r.rack_code = ru.rack_code\n  WHERE ru.status = '占用' GROUP BY 1\n)\nSELECT cap.rk AS key, cap.u_cap AS u_cap, COALESCE(occ.u_occ, 0) AS u_occ\nFROM cap LEFT JOIN occ ON occ.rk = cap.rk\nORDER BY cap.u_cap DESC, cap.rk\n"})
    List<RoomUsageRow> selectRoomUsage();

    @Select({"SELECT event_type AS key, COUNT(*) AS count FROM asset_event WHERE event_type IS NOT NULL AND TRIM(event_type) <> '' GROUP BY 1 ORDER BY count DESC, 1"})
    List<GroupCount> selectEventTypeCounts();

    @Select({"SELECT to_char(date_trunc('month', rack_date), 'YYYY-MM') AS key, COUNT(*) AS count FROM asset WHERE deleted_at IS NULL AND rack_date IS NOT NULL AND rack_date >= #{from} GROUP BY 1 ORDER BY 1"})
    List<GroupCount> selectRackTrend(@Param("from") LocalDate from);

    @Select({"SELECT to_char(date_trunc('month', purchase_date), 'YYYY-MM') AS key, COUNT(*) AS count FROM asset WHERE deleted_at IS NULL AND purchase_date IS NOT NULL AND purchase_date >= #{from} GROUP BY 1 ORDER BY 1"})
    List<GroupCount> selectPurchaseTrend(@Param("from") LocalDate from);

    @Select({"SELECT COUNT(*) FROM asset WHERE deleted_at IS NULL AND rack_date >= CURRENT_DATE - 30"})
    Long countRecentRacked();

    @Select({"<script>\nSELECT\n<choose>\n  <when test=\"dim == 'statusBiz'\">status_biz</when>\n  <when test=\"dim == 'regionCode'\">COALESCE(NULLIF(TRIM(region_code), ''), '未分配')</when>\n  <when test=\"dim == 'categoryCode'\">COALESCE(NULLIF(TRIM(category_code), ''), '未分类')</when>\n  <otherwise>owner_type</otherwise>\n</choose>\nAS key, COUNT(*) AS count, COALESCE(SUM(purchase_price), 0) AS total_value\nFROM asset\nWHERE deleted_at IS NULL\nGROUP BY 1\nORDER BY 1\n</script>\n"})
    List<GroupCountValue> groupAssetByDim(@Param("dim") String dim);

    @Select({"<script>\nSELECT\n<choose>\n  <when test=\"dim == 'status'\">status</when>\n  <when test=\"dim == 'woType'\">wo_type</when>\n  <when test=\"dim == 'rootCause'\">COALESCE(NULLIF(BTRIM(root_cause), ''), '未填写')</when>\n  <otherwise>priority</otherwise>\n</choose>\nAS key, COUNT(*) AS count\nFROM workorder\nWHERE deleted_at IS NULL\nGROUP BY 1\nORDER BY 1\n</script>\n"})
    List<GroupCount> groupWoByDim(@Param("dim") String dim);

    @Select({"SELECT COUNT(*) AS total, COUNT(first_response_at) AS responded, COUNT(*) FILTER (WHERE sla_resolve_deadline IS NOT NULL AND sla_resolve_deadline < now() AND status NOT IN ('已完成', '已关闭', '已驳回')) AS overdue FROM workorder WHERE deleted_at IS NULL"})
    SlaRow selectSla();

    @Select({"SELECT material_code, name, category, unit, stock, safety_stock, max_stock, total_in, total_out, price, warehouse_code FROM material ORDER BY material_code"})
    List<MaterialStockRow> selectMaterials();

    @Select({"SELECT m.material_code, m.name, m.category, m.unit, m.stock, m.safety_stock, m.max_stock, m.total_in, m.total_out, m.price, m.warehouse_code FROM material m WHERE EXISTS (SELECT 1 FROM stock_level sl WHERE sl.material_code = m.material_code AND sl.warehouse_code = #{warehouseCode}) ORDER BY m.material_code"})
    List<MaterialStockRow> selectMaterialsInWarehouse(@Param("warehouseCode") String warehouseCode);

    @Select({"SELECT w.warehouse_code, w.name, w.warehouse_type, w.status, COUNT(DISTINCT sl.material_code)::int AS material_count, COALESCE(SUM(sl.qty), 0)::int AS stock_qty, COALESCE(SUM(sl.qty * COALESCE(m.price, 0)), 0) AS stock_value, COUNT(DISTINCT CASE WHEN sl.qty > 0 AND COALESCE(m.safety_stock, 0) > 0 AND sl.qty <= m.safety_stock THEN sl.material_code END)::int AS low_count, COUNT(DISTINCT CASE WHEN sl.qty = 0 THEN sl.material_code END)::int AS out_count FROM warehouse w LEFT JOIN stock_level sl ON sl.warehouse_code = w.warehouse_code LEFT JOIN material m ON m.material_code = sl.material_code GROUP BY w.warehouse_code, w.name, w.warehouse_type, w.status ORDER BY w.warehouse_code"})
    List<WarehouseStockAggRow> selectWarehouseStockAgg();

    @Select({"<script>\nWITH rw AS (\n  SELECT wo_no, finish_time, first_response_at, root_cause, apply_time\n  FROM workorder\n  WHERE deleted_at IS NULL AND\nwo_type IN ('故障维修', '部件更换')        AND apply_time IS NOT NULL\n        AND apply_time &gt;= ((#{from}::date)::timestamp AT TIME ZONE 'Asia/Shanghai')\n        AND apply_time &lt; (((#{to}::date) + 1)::timestamp AT TIME ZONE 'Asia/Shanghai')\n),\ndone AS (SELECT * FROM rw WHERE finish_time IS NOT NULL),\nstarted AS (\n  SELECT d.wo_no, MIN(f.created_at) AS started_at\n  FROM done d\n  JOIN workorder_flow f ON f.wo_no = d.wo_no AND f.to_status = '处理中'\n  GROUP BY d.wo_no\n),\ndur AS (\n  SELECT GREATEST(EXTRACT(EPOCH FROM (d.finish_time - d.apply_time)), 0) / 3600.0 AS hours,\n         CASE WHEN d.first_response_at IS NULL THEN NULL\n              ELSE GREATEST(EXTRACT(EPOCH FROM (d.first_response_at - d.apply_time)), 0) / 3600.0\n         END AS resp_hours\n  FROM done d\n),\nrep AS (\n  SELECT GREATEST(EXTRACT(EPOCH FROM (d.finish_time - s.started_at)), 0) / 3600.0 AS hours\n  FROM done d JOIN started s ON s.wo_no = d.wo_no\n)\nSELECT\n  (SELECT COUNT(*) FROM rw) AS wo_count,\n  (SELECT COUNT(*) FROM done) AS done_count,\n  (SELECT COUNT(*) FROM rw WHERE root_cause IS NOT NULL AND BTRIM(root_cause) &lt;&gt; '') AS root_cause_count,\n  (SELECT COUNT(*) FROM started) AS repair_sample_count,\n  (SELECT ROUND(AVG(hours)::numeric, 2) FROM rep) AS avg_repair_hours,\n  (SELECT ROUND((percentile_cont(0.5) WITHIN GROUP (ORDER BY hours))::numeric, 2) FROM rep) AS median_repair_hours,\n  (SELECT COUNT(hours) FROM dur) AS sample_count,\n  (SELECT ROUND(AVG(hours)::numeric, 2) FROM dur) AS avg_hours,\n  (SELECT ROUND((percentile_cont(0.5) WITHIN GROUP (ORDER BY hours))::numeric, 2) FROM dur) AS median_hours,\n  (SELECT ROUND((percentile_cont(0.9) WITHIN GROUP (ORDER BY hours))::numeric, 2) FROM dur) AS p90_hours,\n  (SELECT ROUND(MAX(hours)::numeric, 2) FROM dur) AS max_hours,\n  (SELECT ROUND(MIN(hours)::numeric, 2) FROM dur) AS min_hours,\n  (SELECT COUNT(resp_hours) FROM dur) AS response_sample_count,\n  (SELECT ROUND(AVG(resp_hours)::numeric, 2) FROM dur) AS avg_response_hours,\n  (SELECT ROUND((percentile_cont(0.5) WITHIN GROUP (ORDER BY resp_hours))::numeric, 2) FROM dur) AS median_response_hours\n</script>\n"})
    WoDurationRow selectRepairDuration(@Param("from") String from, @Param("to") String to);

    @Select({"<script>\nWITH bounds AS (\n  SELECT ((#{from}::date)::timestamp AT TIME ZONE 'Asia/Shanghai') AS s,\n         (((#{to}::date) + 1)::timestamp AT TIME ZONE 'Asia/Shanghai') AS e,\n         (#{from}::date)::timestamp AS s_wall,\n         ((#{to}::date) + 1)::timestamp AS e_wall\n),\nseries AS (\n  SELECT date_trunc(#{trunc}, g) AS bucket\n  FROM bounds,\n       generate_series(date_trunc(#{trunc}, bounds.s_wall),\n                       date_trunc(#{trunc}, bounds.e_wall - interval '1 millisecond'),\n                       #{step}::interval) g\n),\nwo AS (\n  SELECT date_trunc(#{trunc}, apply_time AT TIME ZONE 'Asia/Shanghai') AS bucket,\n         apply_time, finish_time\n  FROM workorder\n  WHERE deleted_at IS NULL AND\nwo_type IN ('故障维修', '部件更换')        AND apply_time IS NOT NULL\n        AND apply_time &gt;= (SELECT s FROM bounds)\n        AND apply_time &lt; (SELECT e FROM bounds)\n)\nSELECT to_char(s.bucket, #{fmt}) AS bucket,\n       COUNT(w.apply_time)::int AS count,\n       COUNT(w.finish_time)::int AS done_count,\n       ROUND(AVG(CASE WHEN w.finish_time IS NOT NULL\n                THEN GREATEST(EXTRACT(EPOCH FROM (w.finish_time - w.apply_time)), 0) / 3600.0\n                END)::numeric, 2) AS avg_hours\nFROM series s\nLEFT JOIN wo w ON w.bucket = s.bucket\nGROUP BY s.bucket\nORDER BY s.bucket\n</script>\n"})
    List<WoTrendRow> selectRepairTrend(
        @Param("from") String from, @Param("to") String to, @Param("trunc") String trunc, @Param("step") String step, @Param("fmt") String fmt
    );

    @Select({"<script>\nSELECT COUNT(DISTINCT sl.ref_wo) AS wo_count,\n       COUNT(DISTINCT sl.material_code) AS material_count,\n       COALESCE(SUM(sl.qty), 0)::int AS total_qty,\n       COALESCE(SUM(sl.qty * COALESCE(m.price, 0)), 0) AS total_value\nFROM stock_log sl\nLEFT JOIN material m ON m.material_code = sl.material_code\nWHERE sl.log_type = 'OUT'\n  AND sl.ref_wo IS NOT NULL AND BTRIM(sl.ref_wo) &lt;&gt; ''\n  AND sl.created_at &gt;= ((#{from}::date)::timestamp AT TIME ZONE 'Asia/Shanghai')\n  AND sl.created_at &lt; (((#{to}::date) + 1)::timestamp AT TIME ZONE 'Asia/Shanghai')\n</script>\n"})
    WoConsumptionSummaryRow selectWoConsumptionSummary(@Param("from") String from, @Param("to") String to);

    @Select({"<script>\nSELECT sl.ref_wo AS wo_no, w.wo_type, w.title, w.status,\n       COUNT(*)::int AS log_count,\n       COUNT(DISTINCT sl.material_code)::int AS material_count,\n       COALESCE(SUM(sl.qty), 0)::int AS qty,\n       COALESCE(SUM(sl.qty * COALESCE(m.price, 0)), 0) AS value\nFROM stock_log sl\nLEFT JOIN material m ON m.material_code = sl.material_code\nLEFT JOIN workorder w ON w.wo_no = sl.ref_wo\nWHERE sl.log_type = 'OUT'\n  AND sl.ref_wo IS NOT NULL AND BTRIM(sl.ref_wo) &lt;&gt; ''\n  AND sl.created_at &gt;= ((#{from}::date)::timestamp AT TIME ZONE 'Asia/Shanghai')\n  AND sl.created_at &lt; (((#{to}::date) + 1)::timestamp AT TIME ZONE 'Asia/Shanghai')\nGROUP BY sl.ref_wo, w.wo_type, w.title, w.status\nORDER BY value DESC, qty DESC, sl.ref_wo\nLIMIT #{limit}\n</script>\n"})
    List<WoConsumptionRow> selectWoConsumptionTop(@Param("from") String from, @Param("to") String to, @Param("limit") int limit);

    @Select({"<script>\nSELECT sl.material_code, m.name, m.unit,\n       COALESCE(SUM(sl.qty), 0)::int AS qty,\n       COALESCE(SUM(sl.qty * COALESCE(m.price, 0)), 0) AS value,\n       COUNT(DISTINCT sl.ref_wo)::int AS wo_count\nFROM stock_log sl\nLEFT JOIN material m ON m.material_code = sl.material_code\nWHERE sl.log_type = 'OUT'\n  AND sl.ref_wo IS NOT NULL AND BTRIM(sl.ref_wo) &lt;&gt; ''\n  AND sl.created_at &gt;= ((#{from}::date)::timestamp AT TIME ZONE 'Asia/Shanghai')\n  AND sl.created_at &lt; (((#{to}::date) + 1)::timestamp AT TIME ZONE 'Asia/Shanghai')\nGROUP BY sl.material_code, m.name, m.unit\nORDER BY value DESC, qty DESC, sl.material_code\nLIMIT #{limit}\n</script>\n"})
    List<MaterialConsumptionRow> selectMaterialConsumptionTop(@Param("from") String from, @Param("to") String to, @Param("limit") int limit);

    @Select({"<script>\nSELECT BTRIM(root_cause) AS key, COUNT(*) AS count\nFROM workorder\nWHERE deleted_at IS NULL AND\nwo_type IN ('故障维修', '部件更换')      AND root_cause IS NOT NULL AND BTRIM(root_cause) &lt;&gt; ''\n      AND apply_time IS NOT NULL\n      AND apply_time &gt;= ((#{from}::date)::timestamp AT TIME ZONE 'Asia/Shanghai')\n      AND apply_time &lt; (((#{to}::date) + 1)::timestamp AT TIME ZONE 'Asia/Shanghai')\nGROUP BY 1\nORDER BY count DESC, key\n</script>\n"})
    List<GroupCount> selectRepairRootCauses(@Param("from") String from, @Param("to") String to);

    @Select({"<script>\nWITH w AS (\n  SELECT wo_no, cost_labor, cost_service, cost_other\n  FROM workorder\n  WHERE deleted_at IS NULL AND\nwo_type IN ('故障维修', '部件更换')        AND apply_time IS NOT NULL\n        AND apply_time &gt;= ((#{from}::date)::timestamp AT TIME ZONE 'Asia/Shanghai')\n        AND apply_time &lt; (((#{to}::date) + 1)::timestamp AT TIME ZONE 'Asia/Shanghai')\n),\np AS (\n  SELECT sl.ref_wo, SUM(sl.qty * COALESCE(m.price, 0)) AS parts_value\n  FROM stock_log sl\n  LEFT JOIN material m ON m.material_code = sl.material_code\n  WHERE sl.log_type = 'OUT'\n    AND sl.ref_wo IS NOT NULL AND BTRIM(sl.ref_wo) &lt;&gt; ''\n  GROUP BY sl.ref_wo\n)\nSELECT COUNT(*) AS wo_count,\n       COALESCE(SUM(w.cost_labor), 0) AS labor_value,\n       COALESCE(SUM(w.cost_service), 0) AS service_value,\n       COALESCE(SUM(w.cost_other), 0) AS other_value,\n       COALESCE(SUM(COALESCE(p.parts_value, 0)), 0) AS parts_value,\n       COUNT(p.parts_value) AS parts_wo_count\nFROM w LEFT JOIN p ON p.ref_wo = w.wo_no\n</script>\n"})
    WoCostSummaryRow selectRepairCostSummary(@Param("from") String from, @Param("to") String to);

    @Select({"<script>\nWITH w AS (\n  SELECT wo_no, wo_type, title, status, cost_labor, cost_service, cost_other\n  FROM workorder\n  WHERE deleted_at IS NULL AND\nwo_type IN ('故障维修', '部件更换')        AND apply_time IS NOT NULL\n        AND apply_time &gt;= ((#{from}::date)::timestamp AT TIME ZONE 'Asia/Shanghai')\n        AND apply_time &lt; (((#{to}::date) + 1)::timestamp AT TIME ZONE 'Asia/Shanghai')\n),\np AS (\n  SELECT sl.ref_wo, SUM(sl.qty * COALESCE(m.price, 0)) AS parts_value\n  FROM stock_log sl\n  LEFT JOIN material m ON m.material_code = sl.material_code\n  WHERE sl.log_type = 'OUT'\n    AND sl.ref_wo IS NOT NULL AND BTRIM(sl.ref_wo) &lt;&gt; ''\n  GROUP BY sl.ref_wo\n)\nSELECT w.wo_no, w.wo_type, w.title, w.status,\n       w.cost_labor AS labor_value,\n       w.cost_service AS service_value,\n       w.cost_other AS other_value,\n       COALESCE(p.parts_value, 0) AS parts_value,\n       (w.cost_labor + w.cost_service + w.cost_other + COALESCE(p.parts_value, 0)) AS total_value\nFROM w LEFT JOIN p ON p.ref_wo = w.wo_no\nORDER BY total_value DESC, w.wo_no\nLIMIT #{limit}\n</script>\n"})
    List<WoCostRow> selectRepairCostTop(@Param("from") String from, @Param("to") String to, @Param("limit") int limit);
}
