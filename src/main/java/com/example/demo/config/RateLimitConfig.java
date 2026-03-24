package com.example.demo.config;

import com.example.demo.utils.limiter.RateLimit;
import com.example.demo.exception.RateLimitException;
import com.example.demo.utils.limiter.RateLimitContextHolder;
import com.example.demo.utils.limiter.RateLimitType;
import com.example.demo.utils.limiter.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;

@Aspect
@Configuration
@Slf4j
public class RateLimitConfig {
    private static final boolean RATE_LIMIT_ENABLED = true;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private RateLimitContextHolder contextHolder;

    @Pointcut("@annotation(com.example.demo.utils.limiter.RateLimit) || @within(com.example.demo.utils.limiter.RateLimit)")
    public void rateLimitPointcut() {}

    @Around("rateLimitPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!RATE_LIMIT_ENABLED) {
            return joinPoint.proceed();
        }

        RateLimit annotation = getAnnotation(joinPoint);
        if (annotation == null) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String apiKey = joinPoint.getTarget().getClass().getSimpleName()
                + ":" + signature.getMethod().getName();

        if (annotation.globalEnable()
                && rateLimiter.isRateLimited("global", apiKey, annotation.globalCount(), annotation.globalWindow())) {
            throw new RateLimitException("[GLOBAL] " + annotation.message(), RateLimitType.GLOBAL);
        }

        if (annotation.ipEnable()) {
            String clientIp = contextHolder.getClientIp();
            if (rateLimiter.isRateLimited("ip:" + clientIp, apiKey, annotation.ipCount(), annotation.ipWindow())) {
                throw new RateLimitException("[IP] " + annotation.message(), RateLimitType.IP);
            }
        }

        if (annotation.userEnable()) {
            String userId = contextHolder.getCurrentUserId();
            if (userId != null) {
                if (rateLimiter.isRateLimited("user:" + userId, apiKey, annotation.userCount(), annotation.userWindow())) {
                    throw new RateLimitException("[USER] " + annotation.message(), RateLimitType.USER);
                }
            } else {
                String clientIp = contextHolder.getClientIp();
                if (rateLimiter.isRateLimited("anon:" + clientIp, apiKey, annotation.userCount(), annotation.userWindow())) {
                    throw new RateLimitException("[ANON] " + annotation.message(), RateLimitType.USER);
                }
            }
        }

        return joinPoint.proceed();
    }

    private RateLimit getAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit annotation = method.getAnnotation(RateLimit.class);
        if (annotation == null) {
            annotation = joinPoint.getTarget().getClass().getAnnotation(RateLimit.class);
        }
        return annotation;
    }
}
