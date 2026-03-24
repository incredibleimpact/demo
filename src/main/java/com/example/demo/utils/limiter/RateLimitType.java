package com.example.demo.utils.limiter;

// 限流类型枚举
public enum RateLimitType {
    GLOBAL("全局限流"), IP("IP限流"), USER("用户限流");

    private final String desc;
    RateLimitType(String desc) { this.desc = desc; }
    public String getDesc() { return desc; }
}

