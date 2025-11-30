package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.BloomFilterUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate stringRedisTemplate;

    @Resource
    private BloomFilterUtil bloomFilterUtil;

    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result queryShopById(Long id) {
        // 布隆过滤器+定时清理 ：缓存穿透
        //Shop shop = qureyShopWithPassThrough(id);

        // 互斥锁 ： 缓存击穿
        Shop shop = queryShopWithMutex(id);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 解决缓存击穿：使用互斥锁
     * 缓存击穿：热点key过期，大量并发请求同时访问数据库
     * 解决：使用分布式锁，确保只有一个线程重建缓存
     * 
     * @param id 店铺ID
     * @return 店铺信息
     */
    public Shop queryShopWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;
        
        // 1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        
        // 2.判断缓存是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        
        // 3.判断命中的是否是空值（解决缓存穿透）
        if (shopJson != null) {
            // 空值，返回null
            return null;
        }
        
        // 4.实现缓存重建，使用互斥锁
        Shop shop = null;
        int retryCount = 0;
        int maxRetries = 3; // 最大重试次数
        boolean isLocked = false;
        
        while (retryCount < maxRetries) {
            try {
                // 4.1 尝试获取互斥锁
                boolean isLock = tryLock(lockKey);
                
                if (!isLock) {
                    // 4.2 获取锁失败，休眠后重试
                    retryCount++;
                    if (retryCount >= maxRetries) {
                        // 重试次数用完，返回null
                        return null;
                    }
                    Thread.sleep(50);
                    continue; // 继续重试
                }
                
                // 标记已获取锁
                isLocked = true;
                
                // 4.3 获取锁成功，双重检查缓存（重要：其他线程可能已经重建了缓存）
                shopJson = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(shopJson)) {
                    // 其他线程已经重建了缓存，直接返回
                    return JSONUtil.toBean(shopJson, Shop.class);
                }
                
                // 4.4 根据id查询数据库
                shop = getById(id);
                
                // 5.数据库不存在，写入空值到缓存
                if (shop == null) {
                    stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return null;
                }
                
                // 6.数据库存在，写入redis缓存
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return shop; // 成功，返回结果
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("获取锁时被中断", e);
            } catch (Exception e) {
                throw new RuntimeException("查询店铺异常", e);
            } finally {
                // 确保锁被释放
                if (isLocked) {
                    unlock(lockKey);
                    isLocked = false;
                }
            }
        }
        
        return shop;
    }

    /**
     * 缓存穿透
     * @param id
     * @return
     */
    public Shop qureyShopWithPassThrough(Long id){
        // 【布隆过滤器】先检查布隆过滤器，如果不存在则直接返回，避免缓存穿透
        String shopIdStr = String.valueOf(id);
        if (!bloomFilterUtil.mightContain(BLOOM_FILTER_SHOP_KEY, shopIdStr)) {
            return null;
        }

        // 1.从redis中查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存是否命中
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.命中
            Shop shop = BeanUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断命中的是否是空值
        if (shopJson != null) {
            return null;
        }

        // 4.未命中，查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            // 5.数据库不存在
            // 【补充防护】虽然布隆过滤器已经过滤了大部分不存在的ID，
            // 但以下场景仍需要缓存空对象：
            // 1. 布隆过滤器误判（说存在但实际不存在）
            // 2. 店铺被删除后，布隆过滤器仍可能判断存在
            // 3. 初始化后的新增/删除数据的时间差
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6.数据库存在，写入缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7.返回
        return shop;
    }

    /**
     * 尝试获取锁
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

    /**
     * 释放锁
     * @param key 锁的key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id=shop.getId();
        if (id==null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
