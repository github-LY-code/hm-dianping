package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTask = new ArrayBlockingQueue<>(1024*1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 获取队列的订单信息
                    VoucherOrder voucherOrder = orderTask.take();
                    // 创建订单
                    createVoucherOrder(voucherOrder);
                } catch (Exception e) {
                   log.error("处理订单异常", e);
                }
            }
        }
    }

    private void createVoucherOrder(VoucherOrder voucherOrder) {
        Long voucherId = voucherOrder.getVoucherId();
        Long userId = voucherOrder.getUserId();
        // 一人一单
        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:"+userId, stringRedisTemplate);
        //boolean locked = simpleRedisLock.tryLock(1200L);
        RLock lock = redissonClient.getLock("order:"+userId);
        boolean locked = lock.tryLock();
        if (!locked) {
            log.error("你已经领取过此优惠卷！");
            return;
        }
        try {
            Long count = query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherId)
                    .count();
            if (count > 0) {
                log.error("你已拥有此优惠卷！");
                return;
            }

            // 扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!success) {
                log.error("卷已被抢空！");
                return;
            }

            // 保存
            save(voucherOrder);
        } finally {
            // 释放锁
            //simpleRedisLock.unlock();
            lock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 执行lua脚本
        Long res = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), UserHolder.getUser().getId().toString());
        // 判断结果是否为0
        int r = res.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "此券已被抢光":"你已拥有此优惠卷！");
        }
        // 返回订单ID
        long orderId = redisIdWorker.nextId("order");
        // 保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        orderTask.add(voucherOrder);

        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 查询优惠卷
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        // 判断秒杀是否开始
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀未开始！");
//        }
//        // 判断秒杀是否结束
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已结束！");
//        }
//        // 判断库存是否充足
//        if (seckillVoucher.getStock() < 1) {
//            return Result.fail("卷已被抢空！");
//        }
//
//        return createVoucherOrder(voucherId);
//    }

//
//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        // 一人一单
//        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:"+userId, stringRedisTemplate);
//        //boolean locked = simpleRedisLock.tryLock(1200L);
//        RLock lock = redissonClient.getLock("order:"+userId);
//        boolean locked = lock.tryLock();
//        if (!locked) {
//            return Result.fail("你已经领取过此优惠卷！");
//        }
//        try {
//            Long count = query()
//                    .eq("user_id", userId)
//                    .eq("voucher_id", voucherId)
//                    .count();
//            if (count > 0) {
//                return Result.fail("你已经使用过此优惠卷！");
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
//        } finally {
//            // 释放锁
//            //simpleRedisLock.unlock();
//            lock.unlock();
//        }
//    }

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
