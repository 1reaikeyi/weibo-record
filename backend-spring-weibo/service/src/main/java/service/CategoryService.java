package service;

import com.baomidou.mybatisplus.extension.service.IService;
import model.entity.Article;
import model.entity.Category;


/**
 * 分类服务接口 - 定义分类相关业务操作
 */
public interface CategoryService extends IService<Category> {
    Category readCache(Long id);
    Boolean updateCache(Category category);
    Boolean deleteCache(Long id);
}