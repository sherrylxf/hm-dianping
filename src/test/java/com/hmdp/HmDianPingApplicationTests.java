package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_LOGIC_EXPIRE;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testSaveShop(){
        // 测试将店铺ID为1的数据保存到Redis，设置逻辑过期时间
        shopService.saveShop2Redis(1L, CACHE_SHOP_LOGIC_EXPIRE);
        System.out.println("店铺数据已保存到Redis，逻辑过期时间为 " + CACHE_SHOP_LOGIC_EXPIRE + " 秒");
        
        // 查看Redis中实际存储的原始JSON（应该包含expireTime和data字段）
        String key = CACHE_SHOP_KEY + 1;
        String redisJson = stringRedisTemplate.opsForValue().get(key);
        System.out.println("=== Redis中存储的原始JSON ===");
        System.out.println(redisJson);
        System.out.println();
        
        // 解析RedisData对象（包含expireTime）
        RedisData redisData = JSONUtil.toBean(redisJson, RedisData.class);
        System.out.println("=== 解析后的RedisData对象 ===");
        System.out.println("过期时间(expireTime): " + redisData.getExpireTime());
        System.out.println("数据(data): " + JSONUtil.toJsonStr(redisData.getData()));
        System.out.println();
        
        // 提取Shop对象
        Shop shop = JSONUtil.toBean(JSONUtil.toJsonStr(redisData.getData()), Shop.class);
        System.out.println("=== 提取的Shop对象 ===");
        System.out.println("店铺名称: " + shop.getName());
        System.out.println("店铺ID: " + shop.getId());
    }
}
