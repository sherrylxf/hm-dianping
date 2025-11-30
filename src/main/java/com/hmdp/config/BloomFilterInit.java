package com.hmdp.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.utils.BloomFilterUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOOM_FILTER_SHOP_KEY;

/**
 * 布隆过滤器初始化
 * 应用启动时自动执行，将所有店铺ID加载到布隆过滤器中
 */
@Slf4j
@Component
public class BloomFilterInit implements CommandLineRunner {

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private BloomFilterUtil bloomFilterUtil;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始初始化布隆过滤器...");
        
        // 查询所有店铺ID
        QueryWrapper<Shop> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id");
        List<Shop> shopList = shopMapper.selectList(queryWrapper);
        
        if (shopList == null || shopList.isEmpty()) {
            log.warn("没有找到店铺数据，跳过布隆过滤器初始化");
            return;
        }
        
        // 将店铺ID转换为字符串列表
        List<String> shopIds = shopList.stream()
                .map(shop -> String.valueOf(shop.getId()))
                .collect(Collectors.toList());
        
        // 批量添加到布隆过滤器
        bloomFilterUtil.addBatch(BLOOM_FILTER_SHOP_KEY, shopIds);
        
        log.info("布隆过滤器初始化完成，共加载 {} 个店铺ID", shopIds.size());
    }
}

