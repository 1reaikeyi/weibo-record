package model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券订单实体类 - 对应数据库tb_voucher_order表
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("voucher_order")
@Builder
public class VoucherOrder implements Serializable {
    /**
     * 订单唯一主键ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 下单用户ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 购买的优惠券ID
     */
    @TableField(value = "voucher_id")
    private Long voucherId;

    /**
     * 支付方式：1余额，2支付宝，3微信
     */
    @TableField(value = "pay_type")
    private Long payType;

    /**
     * 订单状态：1未支付，2已支付，3已核销，4已取消，5退款中，6已退款
     */
    @TableField(value = "status")
    private Long status;

    /**
     * 下单时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /**
     * 订单更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    /**
     * 支付完成时间
     */
    @TableField(value = "pay_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;

    /**
     * 门店核销使用时间
     */
    @TableField(value = "use_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime useTime;

    /**
     * 退款完成时间
     */
    @TableField(value = "refund_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime refundTime;


}