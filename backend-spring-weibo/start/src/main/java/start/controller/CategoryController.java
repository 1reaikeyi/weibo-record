package start.controller;

import common.result.Result;
import lombok.extern.slf4j.Slf4j;
import model.entity.Category;
import org.springframework.transaction.annotation.Transactional;
import service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 分类管理控制器
 * 
 * @author Smart-doc
 * @since 1.0.0
 */
@RestController
@RequestMapping("/category")
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class CategoryController {
    @Autowired
    private CategoryService categoryService;
    
    /**
     * 添加分类
     * 
     * @param category 分类信息
     * @return 结果
     */
    @PostMapping
    public Result createCategory(@RequestBody @Validated Category category) {
        categoryService.save(category);
        return Result.success("createCategory::"+category.getId());
    }
    
    /**
     * 获取所有分类
     * 
     * @return 结果
     */
    @GetMapping
    public Result readCategory() {
        return Result.success(categoryService.list());
    }

    /**
     * 获取分类详情
     * 
     * @param id 分类ID
     * @return 分类详情信息
     */
    @GetMapping("/{id}")
    public Result getById(@PathVariable("id") Long id) {
        return Result.success(categoryService.readCache(id));
    }
    
    /**
     * 更新分类
     * 
     * @param category 分类信息
     * @return 结果
     */
    @PutMapping
    public Result updateCategory(@RequestBody @Validated Category category) {
        categoryService.updateCache(category);
        return Result.success("updateCategory::"+category.getId());
    }
    
    /**
     * 删除分类
     * 
     * @param id 分类ID
     * @return 结果
     */
    @DeleteMapping("/{id}")
    public Result deleteById(@PathVariable("id") Long id) {
        categoryService.deleteCache(id);
        return Result.success("deleteById::"+id);
    }
}