package com.dcim.collector.mapper;

import com.dcim.collector.entity.DiscoverAssetRef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DiscoverAssetQueryMapper {
    @Select({"SELECT asset_no, name, sn, model_code, mgmt_ip FROM asset WHERE deleted_at IS NULL AND sn IS NOT NULL AND UPPER(sn) = #{sn}"})
    DiscoverAssetRef selectBySn(@Param("sn") String snUpper);

    @Select({"SELECT asset_no, name, sn, model_code, mgmt_ip FROM asset WHERE deleted_at IS NULL AND asset_no = #{assetNo}"})
    DiscoverAssetRef selectByAssetNo(@Param("assetNo") String assetNo);
}
