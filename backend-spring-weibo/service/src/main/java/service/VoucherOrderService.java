package service;

import com.baomidou.mybatisplus.extension.service.IService;
import model.entity.VoucherOrder;

/**
 * 优惠券订单服务接口 - 定义优惠券订单相关业务操作
 */
public interface VoucherOrderService extends IService<VoucherOrder> {
    
    /**
     * 支付成功处理逻辑
     * 1. 校验一人一单
     * 2. 扣减库存
     * 3. 保存订单
     * 
     * @param voucherOrder 订单对象
     */
    void paySuccess(VoucherOrder voucherOrder);

    /**
     * 使用redis锁
     * @param voucherOrder
     */
    void secondKill(VoucherOrder voucherOrder);
}