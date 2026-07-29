package service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import common.result.Result;
import model.entity.VoucherOrder;
import model.entity.VoucherSeckill;
import mapper.VoucherSeckillMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import service.VoucherOrderService;
import service.VoucherSeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀优惠券服务实现类 - 实现秒杀优惠券相关业务逻辑
 */
@Service
@Slf4j
public class VoucherSeckillServiceImpl extends ServiceImpl<VoucherSeckillMapper, VoucherSeckill> implements VoucherSeckillService {

    @Override
    public VoucherSeckill voucherSeckillValid(Long id) {
        VoucherSeckill voucherSeckill = super.getById(id);
        if (voucherSeckill == null) {
            throw new RuntimeException("不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucherSeckill.getBeginTime()) || now.isAfter(voucherSeckill.getEndTime())) {
            throw new RuntimeException("不在规定时间段");
        }
        if (voucherSeckill.getStock() <= 0) {
            throw new RuntimeException("结束了");
        }
        return voucherSeckill;
    }


}