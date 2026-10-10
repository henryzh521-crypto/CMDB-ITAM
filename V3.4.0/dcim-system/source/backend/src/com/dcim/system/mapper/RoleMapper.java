package com.dcim.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dcim.system.entity.RoleEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {
    @Delete({"DELETE FROM roles WHERE role_code = #{roleCode} AND deleted_at IS NOT NULL"})
    int purgeTombstone(@Param("roleCode") String roleCode);
}
