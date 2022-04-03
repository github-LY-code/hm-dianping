package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import com.hmdp.service.ILock;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private final String name;
    private final StringRedisTemplate stringRedisTemplate;
    private final String keyPrefix = "lock:";
    private final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(Long timeOut) {
        // 获取线程ID
        String threadId = Thread.currentThread().getId() + ID_PREFIX;
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(keyPrefix + name, threadId, timeOut, TimeUnit.SECONDS);
        return success;
    }

    @Override
    public void unlock() {
        String threadId = Thread.currentThread().getId() + ID_PREFIX;
        // 获取锁中的标识
        String id = stringRedisTemplate.opsForValue().get(keyPrefix + name);
        // 判断是否是自己加的锁
        if (threadId.equals(id)) {
            // 释放锁
            stringRedisTemplate.delete(keyPrefix + name);
        }
    }
}
