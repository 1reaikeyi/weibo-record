package model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sign")
@Builder
public class Sign {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户id
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 签到的年
     */
    @TableField("year")
    private Long year;

    /**
     * 签到的月
     */
    @TableField("month")
    private Long month;
    /**
     * 已签到天数
     */
    @TableField("signed")
    private Long signed;
    /**
     * 未签到天数
     */
    @TableField("not_signed")
    private Long notSigned;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime createTime;


}
