import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import service.id.RedisID;
import start.WeiweiApplication;


import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest(classes = WeiweiApplication.class)
@Slf4j
public class RedisTest {
    @Autowired
    private RedisID redisID;

    @Test
    public void test() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        // 记录开始时间
        long start = System.currentTimeMillis();
        // 每个线程循环生成ID
        for (int i = 0; i < 10; i++) {
            pool.submit(() -> {
                // 持续生成直到超过1秒
                while (System.currentTimeMillis() - start < 1000) {
                    long a = redisID.createId("order");
                    System.out.println(a);
                }
                latch.countDown();
            });
        }
        latch.await();
        System.out.println("10线程1秒内生成订单ID完成");
        pool.shutdown();
    }

    @Autowired
    private RedissonClient redissonClient;
    private static final String LOCK_KEY = "redisson-lock";

    @Test
    public void testRedisson() {
        // 1. 获取锁对象
        RLock lock = redissonClient.getLock(LOCK_KEY);
        // 尝试获取锁，无等待，默认看门狗自动续期
        boolean isLock = lock.tryLock();

        if (!isLock) {
            log.error("获取锁失败, 1");
            return;
        }

        try {
            log.info("获取锁成功, 1");
            // 调用嵌套业务方法
            method2();
            // 执行业务逻辑
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            log.info("释放锁, 1");
            // 释放锁（底层Lua脚本校验当前线程持有锁才删除）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    void method2() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean isLock = lock.tryLock();

        if (!isLock) {
            log.error("获取锁失败, 2");
            return;
        }

        try {
            log.info("获取锁成功, 2");
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            log.info("释放锁, 2");
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}


