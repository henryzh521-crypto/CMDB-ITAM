package com.dcim.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dcim.asset.entity.AssetEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssetMapper extends BaseMapper<AssetEntity> {
    @Select({"WITH RECURSIVE subtree AS (\n    SELECT * FROM asset WHERE asset_no = #{rootNo}\n    UNION ALL\n    SELECT a.* FROM asset a\n    INNER JOIN subtree s ON a.parent_asset_no = s.asset_no\n    WHERE a.deleted_at IS NULL\n)\nSELECT * FROM subtree WHERE deleted_at IS NULL\n"})
    List<AssetEntity> selectSubtree(@Param("rootNo") String rootNo);
}
