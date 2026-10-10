package com.dcim.inspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dcim.inspection.entity.InspectionPlan;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InspectionPlanMapper extends BaseMapper<InspectionPlan> {
    @Select({"SELECT id FROM inspection_plan\nWHERE enabled AND deleted_at IS NULL AND next_run_at IS NOT NULL AND next_run_at <= NOW()\nORDER BY next_run_at LIMIT #{limit}\nFOR UPDATE SKIP LOCKED\n"})
    List<Long> lockDue(@Param("limit") int limit);
}
