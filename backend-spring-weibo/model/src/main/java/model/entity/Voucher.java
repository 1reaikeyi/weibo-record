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
 * 优惠券实体类 - 对应数据库voucher表
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("voucher")
@Builder
public class Voucher implements Serializable {
    /**
     * 优惠券主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 所属门店ID
     */
    @TableField(value = "shop_id")
    private Long shopId;

    /**
     * 优惠券标题
     */
    @TableField(value = "title")
    private String title;

    /**
     * 优惠券副标题
     */
    @TableField(value = "sub_title")
    private String subTitle;

    /**
     * 优惠券使用规则
     */
    @TableField(value = "rules")
    private String rules;

    /**
     * 用户购买支付金额，单位分
     */
    @TableField(value = "pay_value")
    private Long payValue;

    /**
     * 优惠券抵扣金额，单位分
     */
    @TableField(value = "actual_value")
    private Long actualValue;

    /**
     * 券类型：0普通代金券，1秒杀券
     */
    @TableField(value = "type")
    private Long type;

    /**
     * 优惠券状态：1上架，2下架，3过期
     */
    @TableField(value = "status")
    private Long status;

    /**
     * 优惠券创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 优惠券更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}