package com.dcim.rfid.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RfidAssetQueryMapper {
    @Select({"SELECT COUNT(*) FROM asset WHERE deleted_at IS NULL AND UPPER(sn) = #{sn}"})
    long countBySn(@Param("sn") String sn);

    /** 事务级咨询锁：按 SN 串行化“查重-建资产”窗口，返回值仅占位无业务含义（无 DDL 兜底方案） */
    @Select({"SELECT pg_advisory_xact_lock(hashtext(#{sn}))"})
    long lockSn(@Param("sn") String sn);
}
