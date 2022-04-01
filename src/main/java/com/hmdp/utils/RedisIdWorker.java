package com.hmdp.utils;

import org.apache.tomcat.jni.Local;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLOutput;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    private final StringRedisTemplate stringRedisTemplate;
    private final Long BEGIN_TIMESTAMP = 1648771200L;
    private final int COUNT_BITS = 32;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now();
        long nowTimestamp = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowTimestamp - BEGIN_TIMESTAMP;

        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        return timeStamp << COUNT_BITS | increment;

    }

    public static void main(String[] args) {
        System.out.println(LocalDateTime.of(2022,4,1,0,0).toEpochSecond(ZoneOffset.UTC));
    }
}
