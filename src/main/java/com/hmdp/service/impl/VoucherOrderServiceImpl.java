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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;


    @Override
    public Result seckillVoucher(Long voucherId) {
        // 查询优惠卷
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        // 判断秒杀是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始！");
        }
        // 判断秒杀是否结束
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束！");
        }
        // 判断库存是否充足
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("卷已被抢空！");
        }

        return createVoucherOrder(voucherId);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 一人一单
        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:", stringRedisTemplate);
        boolean locked = simpleRedisLock.tryLock(1200L);
        if (!locked) {
            return Result.fail("你已经领取过此优惠卷！");
        }
        try {
            Long count = query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherId)
                    .count();
            if (count > 0) {
                return Result.fail("你已经使用过此优惠卷！");
            }

            // 扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!success) {
                return Result.fail("卷已被抢空！");
            }

            // 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);

            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            // 保存
            save(voucherOrder);
            // 返回订单ID
            return Result.ok(orderId);
        } finally {
            // 释放锁
            simpleRedisLock.unlock();
        }
    }

//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        // 一人一单
//        synchronized (userId.toString().intern()) {
//            Long count = query()
//                    .eq("user_id", userId)
//                    .eq("voucher_id", voucherId)
//                    .count();
//            if (count > 0) {
//                return Result.fail("你已经领取过此优惠卷！");
//            }
//
//            // 扣减库存
//            boolean success = seckillVoucherService.update()
//                    .setSql("stock = stock - 1")
//                    .eq("voucher_id", voucherId)
//                    .gt("stock", 0)
//                    .update();
//            if (!success) {
//                return Result.fail("卷已被抢空！");
//            }
//
//            // 创建订单
//            VoucherOrder voucherOrder = new VoucherOrder();
//            long orderId = redisIdWorker.nextId("order");
//            voucherOrder.setId(orderId);
//
//            voucherOrder.setUserId(userId);
//            voucherOrder.setVoucherId(voucherId);
//            // 保存
//            save(voucherOrder);
//            // 返回订单ID
//            return Result.ok(orderId);
//        }
//    }
}
