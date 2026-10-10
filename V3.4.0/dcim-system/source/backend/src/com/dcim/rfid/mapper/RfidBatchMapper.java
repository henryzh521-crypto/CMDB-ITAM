package com.dcim.rfid.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dcim.rfid.entity.RfidBatchEntity;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RfidBatchMapper extends BaseMapper<RfidBatchEntity> {
    Set<String> COUNTER_COLUMNS = Set.of("item_count", "scanned_count", "confirmed_count");

    @Update({"UPDATE rfid_batch SET status = #{target}, confirmed_at = CASE WHEN #{target} = '已确认' THEN now() ELSE confirmed_at END WHERE batch_no = #{batchNo} AND status = #{expect}"})
    int casStatus(@Param("batchNo") String batchNo, @Param("expect") String expect, @Param("target") String target);

    default int incrCounter(String col, String batchNo) {
        return this.incrCounterRaw(checkCounter(col), batchNo);
    }

    default int decrCounter(String col, String batchNo) {
        return this.decrCounterRaw(checkCounter(col), batchNo);
    }

    private static String checkCounter(String col) {
        if (!COUNTER_COLUMNS.contains(col)) {
            throw new IllegalArgumentException("非法计数器列：" + col);
        } else {
            return col;
        }
    }

    @Update({"UPDATE rfid_batch SET ${col} = ${col} + 1 WHERE batch_no = #{batchNo}"})
    int incrCounterRaw(@Param("col") String col, @Param("batchNo") String batchNo);

    @Update({"UPDATE rfid_batch SET ${col} = GREATEST(${col} - 1, 0) WHERE batch_no = #{batchNo}"})
    int decrCounterRaw(@Param("col") String col, @Param("batchNo") String batchNo);
}
