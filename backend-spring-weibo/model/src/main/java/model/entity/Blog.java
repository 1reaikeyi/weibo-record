package model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 发布笔记表
 */
@Data
@TableName("blog")
public class Blog implements Serializable {

    /**
     * 主键自增ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商户ID
     */
    @TableField("shop_id")
    private Long shopId;

    /**
     * 发布用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 探店笔记标题
     */
    @TableField("title")
    private String title;

    /**
     * 探店照片地址，最多9张，多图逗号分隔
     */
    @TableField("images")
    private String images;

    /**
     * 探店文字描述内容
     */
    @TableField("content")
    private String content;
    /**
     * 是否被点赞
     * 数据库只保存总数量，减少性能
     */
    @TableField(exist = false)
    private Boolean isLiked;
    /**
     * 笔记点赞数量
     */
    @TableField("liked")
    private Long liked;

    /**
     * 笔记评论总数
     */
    @TableField("comments")
    private Long comments;

    /**
     * 笔记创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 笔记更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
