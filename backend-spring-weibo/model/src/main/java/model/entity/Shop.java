package model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shop")
public class Shop {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商铺名称
     */
    @TableField("name")
    private String name;

    /**
     * 商铺类型的id
     */
    @TableField("type_id")
    private Long typeId;

    /**
     * 商铺图片，多个图片以隔开
     */
    @TableField("images")
    private String images;

    /**
     * 商圈，例如陆家嘴
     */
    @TableField("area")
    private String area;

    /**
     * 地址
     */
    @TableField("address")
    private String address;

    /**
     * 经度
     */
    @TableField("x")
    private Double x;

    /**
     * 纬度
     */
    @TableField("y")
    private Double y;

    /**
     * 均价，取整数
     */
    @TableField("avg_price")
    private Long avgPrice;

    /**
     * 销量
     */
    @TableField("sold")
    private Long sold;

    /**
     * 评论数量
     */
    @TableField("comments")
    private Long comments;

    /**
     * 评分，1~5分，乘10保存，避免小数
     */
    @TableField("score")
    private Long score;

    /**
     * 营业时间，例如 10:00-22:00
     */
    @TableField("open_hours")
    private String openHours;

    /**
     * 创建时间
     */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
