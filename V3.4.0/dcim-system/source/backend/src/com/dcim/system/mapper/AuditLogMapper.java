package com.dcim.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dcim.system.entity.AuditLogEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {
    @Select({"SELECT DISTINCT module FROM audit_log WHERE module IS NOT NULL ORDER BY module LIMIT 200"})
    List<String> distinctModules();

    @Select({"SELECT DISTINCT action FROM audit_log WHERE action IS NOT NULL ORDER BY action LIMIT 200"})
    List<String> distinctActions();

    @Select({"SELECT DISTINCT obj_type FROM audit_log WHERE obj_type IS NOT NULL ORDER BY obj_type LIMIT 200"})
    List<String> distinctObjTypes();

    @Select({"SELECT actor FROM audit_log WHERE actor IS NOT NULL GROUP BY actor ORDER BY max(created_at) DESC LIMIT 200"})
    List<String> distinctActors();
}
