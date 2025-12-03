package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Transactional
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result setkill(Long voucherId) {
        // 0. 检查用户登录状态
        if (UserHolder.getUser() == null) {
            return Result.fail("用户未登录");
        }
        Long userid = UserHolder.getUser().getId();
        
        // 1. 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 1.1 判断优惠券是否存在
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        // 3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        // 4.判断库存
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        // 5. 获取分布式锁，防止同一用户重复下单
        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userid);
        boolean isLock = lock.tryLock(1200);
        if (!isLock) {
            // 获取锁失败，返回失败
            return Result.fail("不允许重复下单");
        }

        try {
            // 获取代理对象，确保事务生效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public Result createVoucherOrder(Long voucherId) {
        // 这个方法会被代理调用，确保事务生效
        return getResult(voucherId);
    }

    private Result getResult(Long voucherId) {
        // 1. 再次查询优惠券（双重检查，确保数据最新）
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        
        // 2. 扣减库存: 乐观锁(CAS)
        // 使用 stock > 0 作为条件，确保库存大于0才能扣减
        boolean update = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)  // 库存必须大于0
                .update();
        if (!update) {
            // 更新失败，说明库存不足
            return Result.fail("库存不足");
        }
        
        // 3. 创建订单
        Long userid = UserHolder.getUser().getId();
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userid);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setPayType(1); // 默认余额支付
        voucherOrder.setStatus(1); // 默认未支付
        save(voucherOrder);
        
        // 4. 返回结果
        return Result.ok(orderId);
    }
}
