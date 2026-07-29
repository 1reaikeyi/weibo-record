package model.dto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Builder;
import lombok.Data;
import model.entity.VoucherSeckill;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
@Data
@Builder
public class VoucherDTO implements Serializable {
    /**
     *主键id
     */
    private Long id;
    /**
     * 店铺shopId
     */
    private Long shopId;
    /**
     * 标题
     */
    private String title;
    /**
     * 副标题
     */
    private String subTitle;
    /**
     * 规则
     */
    private String rules;
    /**
     * 用户购买支付金额
     */
    private Long payValue;
    /**
     * 抵扣金额
     */
    private Long actualValue;
    /**
     * 类型
     */
    private Long type;
    /**
     * 状态
     */
    private Long status;
    /**
     * 详细
     */
    private List<VoucherSeckill> voucherSeckillList;
}
