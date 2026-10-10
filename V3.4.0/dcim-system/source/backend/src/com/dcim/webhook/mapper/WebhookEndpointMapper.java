package com.dcim.webhook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dcim.webhook.entity.WebhookEndpoint;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WebhookEndpointMapper extends BaseMapper<WebhookEndpoint> {
}
