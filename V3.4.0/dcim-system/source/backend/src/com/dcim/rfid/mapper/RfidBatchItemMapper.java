package com.dcim.rfid.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dcim.rfid.entity.RfidBatchItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RfidBatchItemMapper extends BaseMapper<RfidBatchItemEntity> {
    @Update({"UPDATE rfid_batch_item SET received_qty = received_qty + 1 WHERE id = #{id} AND received_qty < expected_qty"})
    int incrReceived(@Param("id") Long id);
}
