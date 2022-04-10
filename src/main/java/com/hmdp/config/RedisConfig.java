package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
         config.useSingleServer().setAddress("redis://192.168.0.128:6379");
//        config.useSingleServer().setAddress("redis://1.12.222.169:6379");
        return Redisson.create(config);
    }
}
