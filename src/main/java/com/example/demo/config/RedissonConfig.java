package com.example.demo.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient(){
        //Spring Boot 默认只会自动配置 Spring Data Redis，提供的是：
        //RedisTemplate, StringRedisTemplate, LettuceConnectionFactory
        //如果想使用Redission必须手动配置Bean
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        // 获取RedisClient对象，并交给IOC进行管理
        return Redisson.create(config);
    }
}
