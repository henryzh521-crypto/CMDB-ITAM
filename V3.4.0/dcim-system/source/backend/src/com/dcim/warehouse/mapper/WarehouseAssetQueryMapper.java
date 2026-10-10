package com.dcim.warehouse.mapper;

import com.dcim.warehouse.entity.InventoryAssetRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WarehouseAssetQueryMapper {
    @Select({"<script>\nSELECT asset_no, name, sn, category_code, model_code, rack_code, unit_no, u_height, status_biz\nFROM asset\nWHERE deleted_at IS NULL\n<if test=\"regionCodes != null and regionCodes.size() > 0\">\n  AND region_code IN\n  <foreach item=\"c\" collection=\"regionCodes\" open=\"(\" separator=\",\" close=\")\">#{c}</foreach>\n</if>\n<if test=\"roomCodes != null and roomCodes.size() > 0\">\n  AND room_code IN\n  <foreach item=\"c\" collection=\"roomCodes\" open=\"(\" separator=\",\" close=\")\">#{c}</foreach>\n</if>\n<if test=\"categoryCodes != null and categoryCodes.size() > 0\">\n  AND category_code IN\n  <foreach item=\"c\" collection=\"categoryCodes\" open=\"(\" separator=\",\" close=\")\">#{c}</foreach>\n</if>\n<if test=\"statusBizList != null and statusBizList.size() > 0\">\n  AND status_biz IN\n  <foreach item=\"c\" collection=\"statusBizList\" open=\"(\" separator=\",\" close=\")\">#{c}</foreach>\n</if>\nORDER BY asset_no\n</script>\n"})
    List<InventoryAssetRow> selectByFilter(
        @Param("regionCodes") List<String> regionCodes,
        @Param("roomCodes") List<String> roomCodes,
        @Param("categoryCodes") List<String> categoryCodes,
        @Param("statusBizList") List<String> statusBizList
    );
}
