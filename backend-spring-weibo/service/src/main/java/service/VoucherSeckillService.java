package service;

import com.baomidou.mybatisplus.extension.service.IService;
import model.entity.VoucherOrder;
import model.entity.VoucherSeckill;

/**
 * 秒杀优惠券服务接口 - 定义秒杀优惠券相关业务操作
 */
public interface VoucherSeckillService extends IService<VoucherSeckill> {
    VoucherSeckill voucherSeckillValid(Long id);

}