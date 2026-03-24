package com.example.demo.utils.limiter;

import java.lang.annotation.*;

/**
 * 多维度限流注解
 * 支持在类或方法上使用，方法级优先
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    // ========== 全局限流 ==========
    /** 是否开启全局限流 */
    boolean globalEnable() default false;
    /** 全局限流：时间窗口内最大请求数 */
    int globalCount() default 1000;
    /** 全局限流：时间窗口（秒） */
    int globalWindow() default 1;

    // ========== IP 限流 ==========
    /** 是否开启 IP 限流 */
    boolean ipEnable() default false;
    /** IP 限流：时间窗口内最大请求数 */
    int ipCount() default 100;
    /** IP 限流：时间窗口（秒） */
    int ipWindow() default 1;

    // ========== 用户限流 ==========
    /** 是否开启用户限流 */
    boolean userEnable() default false;
    /** 用户限流：时间窗口内最大请求数 */
    int userCount() default 20;
    /** 用户限流：时间窗口（秒） */
    int userWindow() default 1;

    /** 限流提示信息 */
    String message() default "请求过于频繁，请稍后再试";
}