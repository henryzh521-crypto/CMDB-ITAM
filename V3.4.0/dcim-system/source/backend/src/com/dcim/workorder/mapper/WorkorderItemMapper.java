package com.dcim.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dcim.workorder.entity.WorkorderItemEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkorderItemMapper extends BaseMapper<WorkorderItemEntity> {
}
