package com.dcim.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dcim.workorder.entity.WorkorderEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkorderMapper extends BaseMapper<WorkorderEntity> {
}
