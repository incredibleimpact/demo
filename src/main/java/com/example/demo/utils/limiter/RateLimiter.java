package com.example.demo.utils.limiter;

import com.example.demo.utils.constants.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.Collections;

@Component
@Slf4j
public class RateLimiter {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;
    static {
        RATE_LIMIT_SCRIPT=new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setLocation(new ClassPathResource("rate_limit.lua"));
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    /**
     * 执行限流检查（滑动窗口）
     *
     * @param dimension 限流维度标识（global/ip:xxx/user:xxx）
     * @param apiKey    接口标识
     * @param maxCount  最大请求数
     * @param windowSeconds 时间窗口（秒）
     * @return true=被限流，false=正常放行
     */
    public boolean isRateLimited(String dimension, String apiKey,
                                 int maxCount, int windowSeconds) {
        // 构建 Redis Key：rate_limit:{dimension}:{apiKey}
        try {
            String key = RedisConstants.RATE_LIMIT_KEY + dimension+":"+apiKey;
            long nowMs = System.currentTimeMillis();
            long windowMs = (long) windowSeconds * 1000;
            Long result = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(nowMs),
                    String.valueOf(windowMs),
                    String.valueOf(maxCount)
            );
            boolean limited = result != null && result == 1L;
            if (limited) {
                log.warn("[限流触发] dimension={}, key={}, limit={}/{} s",
                        dimension, key, maxCount, windowSeconds);
            }
            return limited;//0未超限,1已超限
        } catch (Exception e) {
            // Redis 异常时降级放行，避免影响正常业务
            log.error("[限流组件异常] Redis执行失败，降级放行: {}", e.getMessage());
            return false;
        }
    }
}
