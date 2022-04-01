package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisUtils redisUtils;

    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
        // Shop shop = queryWithPassThrough(id);
        // Shop shop = redisUtils.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, CACHE_SHOP_TTL, TimeUnit.MINUTES, this::getById);

        // 互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);
        Shop shop = redisUtils.queryWithMutex(CACHE_SHOP_KEY, LOCK_SHOP_KEY, id, Shop.class, CACHE_SHOP_TTL, TimeUnit.MINUTES, this::getById);

        // 基于逻辑过期解决缓存击穿
        // Shop shop = queryWithLogicExpire(id);

        if (shop == null) {
            Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

//    public Shop queryWithMutex(Long id) {
//        // 从redis查询商品
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        // 商品是否存在
//        if (!StrUtil.isBlank(shopJson)) {
//            // 存在且值不为null 或 “”
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//
//        // 处理缓存穿透
//        if ("".equals(shopJson)) {
//            return null;
//        }
//
//        // 实现缓存重建
//        // 尝试获取互斥
//        boolean success = true;
//        Shop shop = null;
//        try {
//            do {
//                success = tryLock(LOCK_SHOP_KEY+id);
//                if (success) {
//                    break;
//                }
//                // 休眠一会
//                Thread.sleep(50);
//            } while (!success);
//
//            // 不存在从数据库查困
//            shop = getById(id);
//            // 数据库中不存在，返回错误
//            if (shop == null) {
//                // redis写入空值
//                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, "",
//                        CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            // 模拟重建缓存
//            // Thread.sleep(20000);
//            // 存在写入redis
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(shop),
//                    CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        } finally {
//            // 重建完成或发生异常 释放锁
//            unlock(LOCK_SHOP_KEY+id);
//        }
//        // 返回
//        return shop;
//    }

//    private final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

//    public Shop queryWithLogicExpire(Long id) {
//        // 从redis查询商品
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        // 未命中
//        if (StrUtil.isBlank(shopJson)) {
//            return null;
//        }
//        // 命中，反序列化json
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        // 判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            // 未过期 直接返回信息
//            return shop;
//        }
//        // 已过期
//        // 尝试获取锁
//        boolean success = tryLock(CACHE_SHOP_KEY + id);
//        // 成功
//        if (success) {
//            try {
//                CACHE_REBUILD_EXECUTOR.submit(() -> {
//                            this.saveShop2Redis(id, 20L);
//                        }
//                );
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            } finally {
//                // 释放锁
//                unlock(CACHE_SHOP_KEY+id);
//            }
//        }
//        // 获取失败，返回过期信息
//        return shop;
//    }

    public void saveShop2Redis(Long id, Long seconds) {
        Shop shop = getById(id);

        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(seconds));

        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(redisData));
    }

//    public Shop queryWithPassThrough(Long id) {
//        // 从redis查询商品
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        // 商品是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 存在且值不为null 或 “”
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//
//        // 处理缓存穿透
//        if ("".equals(shopJson)) {
//            return null;
//        }
//
//        // 不存在从数据库查寻
//        Shop shop = getById(id);
//        // 数据库中不存在，返回错误
//        if (shop == null) {
//            // redis写入空值
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, "",
//                    CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        // 存在写入redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(shop),
//                CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        // 返回
//        return shop;
//    }

//    public boolean tryLock(String key) {
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1",
//                LOCK_SHOP_TTL, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    public void unlock(String key) {
//        stringRedisTemplate.delete(key);
//    }

    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺不存在！");
        }
        // 更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok();
    }
}
