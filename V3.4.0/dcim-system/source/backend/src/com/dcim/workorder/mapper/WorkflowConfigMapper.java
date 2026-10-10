package com.dcim.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dcim.workorder.entity.WorkflowConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkflowConfigMapper extends BaseMapper<WorkflowConfigEntity> {
    @Select({"SELECT COUNT(*) FROM workflow_config\nWHERE EXISTS (\n    SELECT 1 FROM jsonb_array_elements_text(approval_roles) e\n    WHERE e = #{roleCode}\n)\n"})
    int countApprovalRoleUsage(@Param("roleCode") String roleCode);
}
