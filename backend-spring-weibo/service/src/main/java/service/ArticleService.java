package service;


import com.baomidou.mybatisplus.extension.service.IService;
import model.entity.Article;

import java.util.List;

/**
 * 文章服务接口 - 定义文章相关业务操作
 */
public interface ArticleService extends IService<Article> {
    Article readCache(Long id);
    Boolean updateCache(Article article);
    Boolean deleteCache(Long id);
}