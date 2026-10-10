package com.dcim.finance.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.finance.dto.DepCandidateRow;
import com.dcim.finance.dto.DepreciationRow;
import com.dcim.finance.dto.FinanceAssetVO;
import com.dcim.finance.dto.PeriodRow;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FinanceQueryMapper {
    @Select({"<script>\nSELECT a.asset_no        AS asset_no,\n       a.name            AS asset_name,\n       a.category_code   AS category_code,\n       a.status_biz      AS status_biz,\n       a.status_fin      AS status_fin,\n       COALESCE(f.lease_flag, FALSE)          AS lease_flag,\n       f.origin_value         AS origin_value,\n       f.residual_rate        AS residual_rate,\n       f.life_months          AS life_months,\n       f.dep_method           AS dep_method,\n       f.dep_start_date       AS dep_start_date,\n       COALESCE(f.accumulated_dep, 0)         AS accumulated_dep,\n       f.net_value            AS net_value,\n       COALESCE(f.impairment, 0)              AS impairment\nFROM asset a\nLEFT JOIN asset_finance f ON f.asset_no = a.asset_no\nWHERE a.deleted_at IS NULL\n<if test=\"keyword != null and keyword != ''\">\n  AND (a.asset_no LIKE '%' || #{keyword} || '%' OR a.name LIKE '%' || #{keyword} || '%')\n</if>\n<if test=\"statusFin != null and statusFin != ''\">\n  AND a.status_fin = #{statusFin}\n</if>\n<if test=\"leaseFlag != null\">\n  AND COALESCE(f.lease_flag, FALSE) = #{leaseFlag}\n</if>\nORDER BY a.asset_no\n</script>\n"})
    List<FinanceAssetVO> selectFinancePage(
        Page<FinanceAssetVO> page, @Param("keyword") String keyword, @Param("statusFin") String statusFin, @Param("leaseFlag") Boolean leaseFlag
    );

    @Select({"SELECT a.asset_no        AS asset_no,\n       a.name            AS asset_name,\n       a.category_code   AS category_code,\n       a.status_biz      AS status_biz,\n       a.status_fin      AS status_fin,\n       COALESCE(f.lease_flag, FALSE)          AS lease_flag,\n       f.origin_value         AS origin_value,\n       f.residual_rate        AS residual_rate,\n       f.life_months          AS life_months,\n       f.dep_method           AS dep_method,\n       f.dep_start_date       AS dep_start_date,\n       COALESCE(f.accumulated_dep, 0)         AS accumulated_dep,\n       f.net_value            AS net_value,\n       COALESCE(f.impairment, 0)              AS impairment\nFROM asset a\nLEFT JOIN asset_finance f ON f.asset_no = a.asset_no\nWHERE a.deleted_at IS NULL AND a.asset_no = #{assetNo}\n"})
    FinanceAssetVO selectFinanceByAssetNo(@Param("assetNo") String assetNo);

    @Select({"SELECT status_fin FROM asset WHERE deleted_at IS NULL AND asset_no = #{assetNo}"})
    String selectStatusFin(@Param("assetNo") String assetNo);

    @Select({"SELECT a.asset_no               AS asset_no,\n       a.status_fin             AS status_fin,\n       f.origin_value           AS origin_value,\n       f.residual_rate          AS residual_rate,\n       f.life_months            AS life_months,\n       f.dep_start_date         AS dep_start_date,\n       COALESCE(f.accumulated_dep, 0) AS accumulated_dep,\n       COALESCE(f.impairment, 0)      AS impairment,\n       f.lease_flag             AS lease_flag\nFROM asset a\nJOIN asset_finance f ON f.asset_no = a.asset_no\nWHERE a.deleted_at IS NULL\n  AND a.status_fin IN ('转固计提', '减值', '折旧+利息')\n  AND f.origin_value IS NOT NULL\n  AND f.origin_value > 0\n  AND f.life_months IS NOT NULL\n  AND f.life_months > 0\nORDER BY a.asset_no\n"})
    List<DepCandidateRow> selectDepCandidates();

    @Update({"UPDATE asset\nSET status_fin = #{status}, version = version + 1, updated_at = now()\nWHERE asset_no = #{assetNo}\n"})
    int updateStatusFin(@Param("assetNo") String assetNo, @Param("status") String status);

    @Select({"SELECT r.asset_no                    AS asset_no,\n       a.name                        AS asset_name,\n       r.period                      AS period,\n       r.net_value + r.month_dep     AS opening_net_value,\n       r.month_dep                   AS dep_amount,\n       r.accumulated                 AS accumulated_dep,\n       r.net_value                   AS closing_net_value\nFROM depreciation_record r\nJOIN asset a ON a.asset_no = r.asset_no AND a.deleted_at IS NULL\nWHERE r.period = #{period}\nORDER BY r.asset_no\n"})
    List<DepreciationRow> selectWorksheetPage(Page<DepreciationRow> page, @Param("period") LocalDate period);

    @Select({"SELECT period,\n       COUNT(*)        AS record_count,\n       SUM(month_dep)  AS total_amount\nFROM depreciation_record\nGROUP BY period\nORDER BY period DESC\n"})
    List<PeriodRow> selectPeriods();

    @Select({"SELECT r.asset_no                    AS asset_no,\n       a.name                        AS asset_name,\n       r.period                      AS period,\n       r.net_value + r.month_dep     AS opening_net_value,\n       r.month_dep                   AS dep_amount,\n       r.accumulated                 AS accumulated_dep,\n       r.net_value                   AS closing_net_value\nFROM depreciation_record r\nJOIN asset a ON a.asset_no = r.asset_no AND a.deleted_at IS NULL\nWHERE r.period = #{period}\nORDER BY r.asset_no\n"})
    List<DepreciationRow> selectWorksheet(@Param("period") LocalDate period);
}
