package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private StringRedisTemplate stringRedisTemplate;
    private  String name;
    private  static  final String KET_PREFIX="lock:";
    private  static  final String ID_PREFIX= UUID.randomUUID().toString()+"-";

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程id
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KET_PREFIX+name, threadId, timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        // 获取线程id
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        // 获取锁中的表示
        String id = stringRedisTemplate.opsForValue().get(KET_PREFIX+name);

        // 判断表示是否一致
        if (threadId.equals(id)){
            stringRedisTemplate.delete(KET_PREFIX+name);
        }
    }
}
