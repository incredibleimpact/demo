package com.example.demo.exception;

import com.example.demo.utils.limiter.RateLimitType;

// 限流异常
public class RateLimitException extends RuntimeException {
    private final RateLimitType type;

    public RateLimitException(String message, RateLimitType type) {
        super(message);
        this.type = type;
    }

    public RateLimitType getType() {
        return type;
    }
}
