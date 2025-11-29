package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    private final StringRedisTemplate stringRedisTemplate;

    public ShopTypeServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public List<ShopType> queryTypeList() {
        // 1.从redis中查询
        String key = CACHE_SHOP_TYPE_KEY;
        String typeListJson = stringRedisTemplate.opsForValue().get(key);
        
        // 2.判断缓存是否命中
        if (StrUtil.isNotBlank(typeListJson)) {
            // 3.命中，直接返回
            List<ShopType> typeList = JSONUtil.toList(typeListJson, ShopType.class);
            return typeList;
        }
        
        // 4.未命中，查询数据库，按sort字段排序
        QueryWrapper<ShopType> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("sort");
        List<ShopType> typeList = list(queryWrapper);

        // 5.数据库不存在
        if (typeList == null) {
            return null;
        }
        
        // 6.数据库存在，写入缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList), 
                CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        
        // 7.返回
        return typeList;
    }
}
