package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_LOGIC_EXPIRE;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    /**
     * 主线程开始
     * 创建 CountDownLatch(100) - 等待100个任务
     * 循环100次，提交100个任务到线程池
     * 主线程调用 latch.await() - 等待所有任务完成
     *
     * [100个线程并发执行]
     *   每个线程执行100次ID生成
     *   完成后调用 latch.countDown()
     *   ↓
     * 所有任务完成，计数归零
     *   ↓
     * 主线程继续执行，计算耗时
     * @throws InterruptedException
     */
    @Test
    void testIdWorker() throws InterruptedException {
        // 创建线程池
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        // 创建 CountDownLatch，等待100个任务完成
        CountDownLatch latch = new CountDownLatch(100);
        Runnable task = () -> {
            try {
                // 每个线程执行100次ID生成
                for (int i = 0; i < 100; i++) {
                    long id = redisIdWorker.nextId("order");
                    // 减少输出，只打印前10个ID
                    if (i < 10) {
                        System.out.println("生成的ID：" + id);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        };
        long start = System.currentTimeMillis();
        // 提交100个任务到线程池
        for (int i = 0; i < 100; i++) {
            executorService.submit(task);
        }
        // 等待所有任务完成
        boolean completed = latch.await(5, java.util.concurrent.TimeUnit.MINUTES);
        long end = System.currentTimeMillis();
        if (completed) {
            System.out.println("所有任务完成！耗时：" + (end - start) + " ms");
        } else {
            System.out.println("任务超时！耗时：" + (end - start) + " ms");
        }
        // 关闭线程池
        executorService.shutdown();
    }

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
