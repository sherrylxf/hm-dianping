package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    
    // 逻辑过期时间（秒），用于逻辑过期方案，便于测试和观察效果
    public static final Long CACHE_SHOP_LOGIC_EXPIRE = 10L; // 10秒，可根据需要调整

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shop-type:list";
    public static final Long CACHE_SHOP_TYPE_TTL = 30L;
    
    // 布隆过滤器相关常量
    public static final String BLOOM_FILTER_SHOP_KEY = "bloom:shop";
}
