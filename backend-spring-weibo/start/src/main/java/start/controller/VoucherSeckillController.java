package start.controller;

import common.result.Result;
import start.security.SecurityContextParam;
import model.entity.VoucherOrder;
import model.entity.VoucherSeckill;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.VoucherOrderService;
import service.VoucherSeckillService;
import service.id.RedisID;

/**
 * 使用redisson分布式锁实现秒杀 - 同步版本
 * 支付成功逻辑已迁移至 VoucherOrderService.paySuccess()
 */
@RestController
@RequestMapping("/voucherSeckill")
public class VoucherSeckillController {
    @Autowired
    private VoucherSeckillService voucherSeckillService;
    @Autowired
    private VoucherOrderService voucherOrderService;
    @Autowired
    private RedisID redisID;
    @Autowired
    private RedissonClient redissonClient;

    @PostMapping("/pay")
    public Result redisLock(@RequestBody VoucherOrder voucherOrder) {
        // 校验秒杀活动是否有效
        VoucherSeckill voucherSeckill = voucherSeckillService.voucherSeckillValid(voucherOrder.getVoucherId());
        if (voucherSeckill == null) {
            return Result.error("秒杀活动不存在或已结束");
        }

        // 获取当前用户ID
        Long userId = SecurityContextParam.getCurrentUserId();
        // 设置订单基础信息
        voucherOrder.setId(redisID.createId("orderId"));
        voucherOrder.setUserId(userId);
        voucherOrder.setStatus(1L);
        voucherOrderService.secondKill(voucherOrder);
        return Result.success("paySuccess");
    }
}

