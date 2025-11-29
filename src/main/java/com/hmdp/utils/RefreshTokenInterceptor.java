package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
    }
}
