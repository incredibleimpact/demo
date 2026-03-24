package com.example.demo.utils.idWorker;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDWorker {
    private static final long BEGIN_TIMESTAMP=1640995200L;
    private static final int COUNT_BITS=32;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisIDWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Long nextId(String keyPrefix){
        LocalDateTime now= LocalDateTime.now();
        long nowSecond=now.toEpochSecond(ZoneOffset.UTC);
        long timestamp=nowSecond-BEGIN_TIMESTAMP;
        String date=now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count=stringRedisTemplate.opsForValue().increment("icr:"+keyPrefix+":"+date);
        return timestamp<<COUNT_BITS|count;
    }
}
