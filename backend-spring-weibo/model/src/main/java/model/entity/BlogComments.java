package model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评论区表
 */
@Data
@TableName("blog_comments")
public class BlogComments implements Serializable {

    /**
     * 评论主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 评论发布用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 关联探店笔记ID
     */
    @TableField("blog_id")
    private Long blogId;

    /**
     * 回复blog评论值为0，回复blog_comments评论值为1
     * 默认值为0
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 回复目标评论ID，对其他人的评论主键ID
     */
    @TableField("answer_id")
    private Long answerId;

    /**
     * 评论文字内容
     */
    @TableField("content")
    private String content;

    /**
     * 评论点赞数量
     */
    @TableField("liked")
    private Long liked;

    /**
     * 评论状态：0正常，1被举报，2禁止查看
     */
    @TableField("status")
    private Long status;

    /**
     * 评论创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 评论更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
