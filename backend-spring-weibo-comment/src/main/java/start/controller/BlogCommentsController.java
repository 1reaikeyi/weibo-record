package start.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import common.ThreadLocalContext.ThreadLocalParam;
import common.constant.JwtConstant;
import common.result.Result;
import common.ThreadLocalContext.ThreadLocalContextHolder;
import model.entity.Blog;
import model.entity.BlogComments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import service.BlogCommentsService;
import service.BlogService;

import java.util.Map;

@RestController
@RequestMapping("/comments")
public class BlogCommentsController {
    @Autowired
    private BlogCommentsService blogCommentsService;
    @Autowired
    private BlogService blogService;
    @PostMapping("/0/{id}")
    public Result createBlog(@PathVariable("id") Long id) {
        return Result.success();
    }
    @PostMapping("/1/{id}")
    public Result createBlogComment(@PathVariable("id") Long id) {
        return Result.success();
    }

}
