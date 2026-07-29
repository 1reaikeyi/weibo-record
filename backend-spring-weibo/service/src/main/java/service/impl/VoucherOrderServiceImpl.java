package service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import model.entity.VoucherOrder;
import model.entity.VoucherSeckill;
import mapper.VoucherOrderMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import service.VoucherOrderService;
import service.VoucherSeckillService;

import java.util.concurrent.TimeUnit;

/**
 * 优惠券订单服务实现类 - 实现优惠券订单相关业务逻辑
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements VoucherOrderService {

    @Autowired
    private VoucherSeckillService voucherSeckillService;
    @Autowired
    private RedissonClient redissonClient;
    /**
     * 支付成功处理逻辑
     * 1. 校验一人一单（放在前面，避免重复下单扣减库存）
     * 2. 扣减库存
     * 3. 保存订单
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void paySuccess(VoucherOrder voucherOrder) {
        // 扣库存
        boolean success = voucherSeckillService.lambdaUpdate()
                .eq(VoucherSeckill::getVoucherId, voucherOrder.getVoucherId())
                .gt(VoucherSeckill::getStock, 0)
                .setSql("stock = stock - 1")
                .update();
        if (!success) {
            throw new RuntimeException("库存不够");
        }
        // 一人一单检查（优先检查，避免重复下单扣减库存）
        Long count = this.count(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, voucherOrder.getUserId())
                .eq(VoucherOrder::getVoucherId, voucherOrder.getVoucherId()));
        if (count > 0) {
            throw new RuntimeException("一人一单");
        }
        // 保存订单
        this.save(voucherOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void secondKill(VoucherOrder voucherOrder) {
        // 使用 Redisson 分布式锁防止重复下单
        RLock redisLock = redissonClient.getLock("redisson:voucherSeckill:" + voucherOrder.getUserId() + ":" + voucherOrder.getVoucherId());
        boolean locked = false;
        try {
            // 尝试获取锁，等待5秒
            locked = redisLock.tryLock(5, 30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁失败");
        }

        if (!locked) {
            throw new RuntimeException("请勿重复下单");
        }

        try {
            paySuccess(voucherOrder);
        } finally {
            // 确保锁被释放
            if (locked && redisLock.isHeldByCurrentThread()) {
                redisLock.unlock();
            }
        }
    }
}