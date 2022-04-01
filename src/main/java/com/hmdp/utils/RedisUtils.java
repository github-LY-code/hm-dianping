package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;


@Slf4j
@Component
public class RedisUtils {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key,
                JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Long time, TimeUnit timeUnit, Function<ID, R> function) {
        // 从redis查询商品
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        // 商品是否存在
        if (StrUtil.isNotBlank(json)) {
            // 存在且值不为null 或 “”
            return JSONUtil.toBean(json, type);
        }

        // 处理缓存穿透
        if ("".equals(json)) {
            return null;
        }

        // 不存在从数据库查寻
        R result = function.apply(id);
        // 数据库中不存在，返回错误
        if (result == null) {
            // redis写入空值
            stringRedisTemplate.opsForValue().set(keyPrefix+id, "",
                    time, timeUnit);
            return null;
        }
        // 存在写入redis
        this.set(keyPrefix+id, result, time, timeUnit);
        // 返回
        return result;
    }

    public <R, ID> R queryWithMutex(String keyPrefix, String lockPrefix, ID id, Class<R> type, Long time, TimeUnit timeUnit, Function<ID, R> function) {
        // 从redis查询商品
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        // 商品是否存在
        if (!StrUtil.isBlank(json)) {
            // 存在且值不为null 或 “”
            return JSONUtil.toBean(json, type);
        }

        // 处理缓存穿透
        if ("".equals(json)) {
            return null;
        }

        // 实现缓存重建
        // 尝试获取互斥
        boolean success = true;
        R r = null;
        try {
            do {
                success = tryLock(lockPrefix+id);
                if (success) {
                    break;
                }
                // 休眠一会
                Thread.sleep(50);
            } while (!success);

            // 不存在从数据库查困
            r = function.apply(id);
            // 数据库中不存在，返回错误
            if (r == null) {
                // redis写入空值
                stringRedisTemplate.opsForValue().set(keyPrefix+id, "",
                        time, timeUnit);
                return null;
            }
            // 模拟重建缓存
            // Thread.sleep(20000);
            // 存在写入redis
            this.set(keyPrefix+id, JSONUtil.toJsonStr(r),
                    time, timeUnit);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 重建完成或发生异常 释放锁
            unlock(lockPrefix+id);
        }
        // 返回
        return r;
    }

    public boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1",
                LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    public void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    public <R, ID> R queryWithLogicExpire(String keyPrefix, String lockPrefix, ID id, Class<R> type, Long time, TimeUnit timeUnit, Function<ID, R> function) {
        // 从redis查询商品
        String shopJson = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        // 未命中
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        // 命中，反序列化json
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期 直接返回信息
            return r;
        }
        // 已过期
        // 尝试获取锁
        boolean success = tryLock(lockPrefix + id);
        // 成功
        if (success) {
            try {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    // 重建
                    R r1 = function.apply(id);
                    set(keyPrefix+id, r1, time, timeUnit);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 释放锁
                unlock(lockPrefix+id);
            }
        }
        // 获取失败，返回过期信息
        return r;
    }
}
