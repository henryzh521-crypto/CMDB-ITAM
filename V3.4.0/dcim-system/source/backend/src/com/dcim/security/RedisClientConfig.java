package com.dcim.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(
    proxyBeanMethods = false
)
@Conditional({RedisEnabledCondition.class})
@EnableConfigurationProperties({RedisProperties.class})
public class RedisClientConfig {
    @Bean
    @ConditionalOnMissingBean({RedisConnectionFactory.class})
    public LettuceConnectionFactory redisConnectionFactory(RedisProperties properties) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(properties.getHost(), properties.getPort());
        String password = properties.getPassword();
        if (password != null && !password.isBlank()) {
            factory.setPassword(password);
        }

        return factory;
    }

    @Bean
    @ConditionalOnMissingBean({StringRedisTemplate.class})
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }
}
