package com.example.demo.utils.constants;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String BLOOM_SHOP_KEY = "bloom:shop:id";
    public static final Long CACHE_VOUCHER_LIST_TTL = 10L;
    public static final String CACHE_VOUCHER_LIST_KEY = "cache:voucher:list:";
    public static final Long CACHE_PRODUCT_TTL = 30L;
    public static final String CACHE_PRODUCT_KEY = "cache:product:";
    public static final String CACHE_PRODUCT_SEARCH_KEY = "cache:product:search:";
    public static final String CACHE_CATEGORY_TREE_KEY = "cache:category:tree";
    public static final Long CACHE_HOME_RECOMMEND_TTL = 10L;
    public static final String CACHE_HOME_RECOMMEND_KEY = "cache:home:recommend:";
    public static final String BLOOM_PRODUCT_KEY = "bloom:product:id";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final String LOCK_PRODUCT_KEY = "lock:product:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String SECKILL_ORDER_USER_SET_KEY = "seckill:order:user:";
    public static final String ORDER_OP_LOCK_KEY = "order:op:lock:";
    public static final String ORDER_PAY_CHECK_SENT_KEY = "order:pay:check:sent:";
    public static final String ORDER_PAY_LOCK_KEY="order:pay:lock:";
    public static final String MQ_CONFIRM_CACHE_KEY = "mq:confirm:";

    public static final String RATE_LIMIT_KEY = "rate:limit:";
}
