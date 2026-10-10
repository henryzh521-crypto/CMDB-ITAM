package com.dcim.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(
    proxyBeanMethods = false
)
public class TokenStoreConfig {
    @Bean
    @Conditional({RedisEnabledCondition.class})
    public TokenStore redisTokenStore(StringRedisTemplate redisTemplate) {
        return new RedisTokenStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean({TokenStore.class})
    public TokenStore inMemoryTokenStore() {
        return new InMemoryTokenStore();
    }
}
