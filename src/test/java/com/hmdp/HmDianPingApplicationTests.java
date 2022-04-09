package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    private final ExecutorService es =  Executors.newFixedThreadPool(500);

    @Test
    public void test() {
        shopService.saveShop2Redis(1L, 60L);
    }

    @Test
    public void test1() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(500);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long order = redisIdWorker.nextId("order");
                System.out.println("id: "+order);
            }
            latch.countDown();
        };
        long start = System.currentTimeMillis();
        for (int i = 0;i < 500;i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time: "+(end-start)/1000.0);
    }

    @Test
    public void  test2() {
        System.out.println(redissonClient);
    }
}
