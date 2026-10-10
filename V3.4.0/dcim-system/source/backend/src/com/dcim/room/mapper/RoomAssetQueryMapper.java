package com.dcim.room.mapper;

import com.dcim.room.entity.RoomAssetRef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoomAssetQueryMapper {
    @Select({"SELECT asset_no, name, status_biz FROM asset WHERE deleted_at IS NULL AND asset_no = #{assetNo}"})
    RoomAssetRef selectRefByAssetNo(@Param("assetNo") String assetNo);
}
