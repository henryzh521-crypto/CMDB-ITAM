package com.dcim.webhook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dcim.webhook.entity.WebhookDelivery;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WebhookDeliveryMapper extends BaseMapper<WebhookDelivery> {
    @Select({"SELECT id FROM webhook_delivery\nWHERE status IN ('PENDING', 'RETRY') AND next_attempt_at <= NOW()\nORDER BY next_attempt_at, id\nLIMIT #{limit}\nFOR UPDATE SKIP LOCKED\n"})
    List<Long> lockDue(@Param("limit") int limit);

    @Delete({"DELETE FROM webhook_delivery WHERE created_at < NOW() - make_interval(days => #{days}) AND status IN ('SUCCESS', 'DEAD')"})
    int purgeOlderThan(@Param("days") int days);
}
