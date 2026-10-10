package com.dcim.rfid.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RfidMaterialQueryMapper {
    @Select({"SELECT name FROM material WHERE material_code = #{code}"})
    String selectNameByCode(@Param("code") String code);
}
