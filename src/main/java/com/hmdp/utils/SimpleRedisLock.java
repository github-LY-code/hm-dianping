package com.hmdp.utils;

import com.hmdp.service.ILock;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private final String name;
    private final StringRedisTemplate stringRedisTemplate;
    private final String keyPrefix = "lock:";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(Long timeOut) {
        long id = Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(keyPrefix + name, id + "", timeOut, TimeUnit.SECONDS);
        return success;
    }

    @Override
    public void unlock() {
        // 释放锁
        stringRedisTemplate.delete(keyPrefix+name);
    }
}
