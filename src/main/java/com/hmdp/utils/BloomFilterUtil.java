package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 布隆过滤器工具类
 * 使用Redis BitMap实现，用于解决缓存穿透问题
 */
@Component
public class BloomFilterUtil {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 布隆过滤器的位数组大小（2的32次方，约42亿位）
    private static final long BIT_ARRAY_SIZE = 1L << 32;
    
    // 哈希函数数量（使用多个哈希函数可以降低误判率）
    private static final int HASH_COUNT = 3;

    /**
     * 将元素添加到布隆过滤器
     * @param key Redis键
     * @param value 要添加的值
     */
    public void add(String key, String value) {
        long[] hashValues = getHashValues(value);
        for (long hashValue : hashValues) {
            long bitIndex = hashValue % BIT_ARRAY_SIZE;
            stringRedisTemplate.opsForValue().setBit(key, bitIndex, true);
        }
    }

    /**
     * 批量添加元素到布隆过滤器
     * @param key Redis键
     * @param values 要添加的值列表
     */
    public void addBatch(String key, List<String> values) {
        for (String value : values) {
            add(key, value);
        }
    }

    /**
     * 判断元素是否可能在布隆过滤器中
     * @param key Redis键
     * @param value 要查询的值
     * @return true: 可能存在（可能是误判）; false: 一定不存在
     */
    public boolean mightContain(String key, String value) {
        long[] hashValues = getHashValues(value);
        for (long hashValue : hashValues) {
            long bitIndex = hashValue % BIT_ARRAY_SIZE;
            Boolean bit = stringRedisTemplate.opsForValue().getBit(key, bitIndex);
            // 如果任何一个位为false，则一定不存在
            if (bit == null || !bit) {
                return false;
            }
        }
        // 所有位都为true，可能存在（也可能是误判）
        return true;
    }

    /**
     * 计算元素的多个哈希值
     * 使用MD5、SHA1等不同的哈希算法来模拟多个哈希函数
     */
    private long[] getHashValues(String value) {
        long[] hashValues = new long[HASH_COUNT];
        
        try {
            // 使用不同的哈希算法生成多个哈希值
            hashValues[0] = hashWithAlgorithm(value, "MD5");
            hashValues[1] = hashWithAlgorithm(value, "SHA-1");
            hashValues[2] = hashWithAlgorithm(value, "SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("哈希算法不存在", e);
        }
        
        return hashValues;
    }

    /**
     * 使用指定算法计算哈希值
     */
    private long hashWithAlgorithm(String value, String algorithm) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        
        // 将字节数组转换为long值
        long hash = 0;
        for (int i = 0; i < Math.min(8, hashBytes.length); i++) {
            hash = (hash << 8) | (hashBytes[i] & 0xFF);
        }
        
        return Math.abs(hash);
    }
}





