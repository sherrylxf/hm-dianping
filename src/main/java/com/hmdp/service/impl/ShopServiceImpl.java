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
        // 【布隆过滤器】先检查布隆过滤器，如果不存在则直接返回，避免缓存穿透
        String shopIdStr = String.valueOf(id);
        if (!bloomFilterUtil.mightContain(BLOOM_FILTER_SHOP_KEY, shopIdStr)) {
            // 布隆过滤器判断一定不存在，直接返回，不查询数据库
            return Result.fail("店铺不存在");
        }
        
        // 1.从redis中查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存是否命中
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.命中
            Shop shop = BeanUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        // 判断命中的是否是空值
        if (shopJson != null) {
            return Result.fail("店铺不存在");
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
            return Result.fail("店铺不存在");
        }
        // 6.数据库存在，写入缓存 (定时清理缓存)
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7.返回
        return Result.ok(shop);
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
