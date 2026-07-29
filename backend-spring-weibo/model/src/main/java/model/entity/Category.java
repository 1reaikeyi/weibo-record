package model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分类实体类 - 对应数据库category表
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("category")
@Builder
public class Category implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;//主键ID
    @NotEmpty
    @TableField(value = "category_name")
    private String categoryName;//分类名称
    @NotEmpty
    @TableField(value = "category_alias")
    private String categoryAlias;//分类别名
    @TableField(value = "create_user", fill = FieldFill.INSERT)
    private Long createUser;//创建人ID
    @TableField(value = "update_user", fill = FieldFill.INSERT_UPDATE)
    private Long updateUser;//更新人ID
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;//创建时间
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;//更新时间
//    ### 'update_user' in 'field list' ### The error may exist in mapper/ArticleMapper.java (best guess) ### The error may involve defaultParameterMap ### The error occurred while setting parameters ### SQL: SELECT id,title,content,cover_img,state,category_id,create_user,update_user,create_time,update_time FROM article ### Cause: java.sql.SQLSyntaxErrorException: Unknown column 'update_user' in 'field list' ; bad SQL grammar []去联系管理员
}