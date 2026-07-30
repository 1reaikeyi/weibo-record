package start.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import common.result.Result;
import model.entity.Blog;
import model.entity.BlogComments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import service.BlogCommentsService;
import service.BlogService;
import start.security.SecurityContextParam;

import java.util.Map;

@RestController
@RequestMapping("/comments")
public class BlogCommentsController {
    @Autowired
    private BlogCommentsService blogCommentsService;
    @Autowired
    private BlogService blogService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String BLOG_VIEW_PREFIX = "blog:view:";

    @PostMapping("/0/{id}")
    public Result createBlog(@PathVariable("id") Long id) {
        return Result.success();
    }
    @PostMapping("/1/{id}")
    public Result createBlogComment(@PathVariable("id") Long id) {
        return Result.success();
    }

    @PostMapping("/view")
    public Result view(@RequestBody Long id){
        Long userId = SecurityContextParam.getCurrentUserId();
        stringRedisTemplate.opsForHyperLogLog().add(BLOG_VIEW_PREFIX+id,userId.toString());
        return Result.success("view+1");
    }
    @GetMapping("/view/of/{id}")
    public Result readView(@PathVariable Long id){
        Long count = stringRedisTemplate.opsForHyperLogLog().size(BLOG_VIEW_PREFIX+id);
        return Result.success(count);
    }

}
