package start.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import common.ThreadLocalContext.ThreadLocalParam;
import common.constant.JwtConstant;
import common.result.Result;
import common.ThreadLocalContext.ThreadLocalContextHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import model.dto.VoucherDTO;
import model.entity.Voucher;
import model.entity.VoucherOrder;
import model.entity.VoucherSeckill;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.VoucherOrderService;
import service.VoucherSeckillService;
import service.VoucherService;
import service.lock.ILock;
import service.lock.RedisLock;
import service.id.RedisID;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 使用redisLock,异步处理
 */
@RestController
@RequestMapping("/voucher")
@Slf4j
public class VoucherController {
    @Autowired
    private VoucherService voucherService;
    @Autowired
    private VoucherSeckillService voucherSeckillService;
    @Autowired
    private VoucherOrderService voucherOrderService;
    @Autowired
    private RedisID redisID;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String VOUCHER_STOCK_PREFIX = "voucherSeckill:stock:";
    // Lua脚本：校验库存和重复下单
    private static final DefaultRedisScript<Long> REDIS_SCRIPT = new DefaultRedisScript<>();
    static {
        REDIS_SCRIPT.setLocation(new ClassPathResource("redis-seckill.lua"));
        REDIS_SCRIPT.setResultType(Long.class);
    }
//     异步订单处理队列 - 存储完整的VoucherOrder对象
     private BlockingQueue<VoucherOrder> orderQueue = new ArrayBlockingQueue<>(128 * 1024);

    // 秒杀订单处理线程池 - 单线程确保顺序处理
    private static final ExecutorService SCEKILL_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "voucher-order-handler");
        t.setDaemon(true);
        return t;
    });

    /**
     * 初始化方法 - 启动异步订单处理线程
     */
    @PostConstruct
    public void init() {
        SCEKILL_EXECUTOR.submit(new VoucherController.HandleOrderTaskByList());
    }
    @PreDestroy
    public void destroy() {
        SCEKILL_EXECUTOR.shutdown();
        try {
            // 等待10秒让未处理的订单完成
            if (!SCEKILL_EXECUTOR.awaitTermination(10, TimeUnit.SECONDS)) {
                SCEKILL_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            SCEKILL_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    @PostMapping("/create")
    public Result createVoucher(@RequestBody VoucherDTO voucherDTO) {
        Voucher voucher = BeanUtil.toBean(voucherDTO, Voucher.class);
        voucherService.save(voucher);
        List<VoucherSeckill> voucherSeckillList = voucherDTO.getVoucherSeckillList().stream()
                .map((VoucherSeckill voucherSeckill) -> {return VoucherSeckill.builder()
                        .stock(voucherSeckill.getStock())
                        .beginTime(voucherSeckill.getBeginTime())
                        .endTime(voucherSeckill.getEndTime())
                        .voucherId(voucherDTO.getId())
                        .build();})
                .toList();
        for (int i = 0; i < voucherSeckillList.size(); i++) {
            stringRedisTemplate.opsForValue().set(VOUCHER_STOCK_PREFIX+voucher.getId(),
                    voucherSeckillList.get(i).getStock().toString());
        }
        voucherSeckillService.saveBatch(voucherSeckillList);
        return Result.success("createVoucher");
    }

    @PostMapping("/pay")
    public Result redisLock(@RequestBody VoucherOrder voucherOrder) {
        VoucherSeckill voucherSeckill = voucherSeckillService.voucherSeckillValid(voucherOrder.getVoucherId());
        if (voucherSeckill == null) {
            return Result.error("秒杀活动不存在或已结束");
        }
        Long userId = ThreadLocalParam.getUserId();
        Long orderId = redisID.createId("pay");
//        执行lua
        Long result = stringRedisTemplate.execute(REDIS_SCRIPT,
                List.of(),
                voucherOrder.getVoucherId().toString(),
                userId.toString(),
                orderId.toString());
        if (result != 0) {
            return Result.error(result == 1 ? "库存不够" : "重复下单");
        }
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setStatus(1L);
        boolean offer = orderQueue.offer(voucherOrder);
        if (!offer) {
            return Result.error("系统繁忙，请稍后重试");
        }
         return Result.success(orderId);
    }
    private class HandleOrderTaskByList implements Runnable {
        @Override
        public void run() {
            while (true) {
                 try {
                    // 从队列中取出订单（阻塞等待）
                    VoucherOrder voucherOrder = orderQueue.take();
                    log.info("检验值voucherOrder:{}", voucherOrder);
                    ILock redisLock = new RedisLock(stringRedisTemplate,
                            "redisson:voucherSeckill:" + voucherOrder.getUserId() + ":" + voucherOrder.getVoucherId());
                    boolean locked = false;
                    locked = redisLock.getLocked(10);
                    if (!locked) {
                       continue;
                    }
                    try {
                    // 执行实际的下单逻辑
                    voucherOrderService.paySuccess(voucherOrder);
                    } finally {
                        redisLock.unlock();
                    }

                 } catch (Exception e) {
                     // 线程被中断，退出循环
                     Thread.currentThread().interrupt();
                     break;
                 }
            }
        }
    }
}
