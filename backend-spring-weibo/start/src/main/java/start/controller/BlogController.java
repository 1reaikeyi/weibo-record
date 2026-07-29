package start.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import common.result.Result;
import common.result.ScrollResult;
import jakarta.websocket.server.PathParam;
import lombok.extern.slf4j.Slf4j;
import model.dto.BlogDTO;
import model.entity.Blog;
import model.entity.User;
import model.entity.UserFollow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.*;
import service.BlogService;
import service.UserFollowService;
import service.UserService;
import start.security.SecurityContextParam;

import java.util.*;

@RestController
@RequestMapping("/blog")
@Slf4j
public class BlogController {
    private static final Logger log = LoggerFactory.getLogger(BlogController.class);
    @Autowired
    private BlogService blogService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserFollowService userFollowService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String BLOG_LIKED_PREFIX = "blog:liked:";
    private static final String BLOG_FOLLOW_PREFIX = "blog:follow:";

    @PostMapping
    public Result createBlog(@RequestBody BlogDTO blogDTO) {
        Long userId = SecurityContextParam.getCurrentUserId();
        Blog blog = BeanUtil.toBean(blogDTO, Blog.class);
        blog.setUserId(userId);
        blogService.save(blog);
        //发送给follower的收件箱
        List<UserFollow> userFollows = userFollowService.list(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowUserId, userId));
        for (UserFollow userFollow : userFollows) {
            String key = BLOG_FOLLOW_PREFIX + userFollow.getUserId();
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        return Result.success("createBlog::"+blog.getId());
    }
    @GetMapping("/{id}")
    public Result readBlogById(@PathVariable Long id) {
        return Result.success(blogService.getById(id));
    }
    @GetMapping("/all")
    public Result readBlog() {
        return Result.success(blogService.list());
    }
    @PostMapping("/liked/{id}")
    public Result isliked(@PathVariable Long id) {
        Long userId = SecurityContextParam.getCurrentUserId();
        Double liked = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_PREFIX + id,userId.toString());
        if (liked == null) {
            //没点赞
            boolean success = blogService.lambdaUpdate()
                    .setSql("liked= liked + 1").eq(Blog::getId,id).update();
            if(success){
                stringRedisTemplate.opsForZSet().add(BLOG_LIKED_PREFIX + id,userId.toString(),System.currentTimeMillis());
            }
            return Result.success("liked::"+id);
        }else {
            //点赞过
            boolean success = blogService.lambdaUpdate()
                    .setSql("liked= liked - 1").eq(Blog::getId,id).update();
            if(success){
                stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_PREFIX + id,userId.toString());
            }
            return Result.success("unliked::"+id);
        }
    }
    @GetMapping("liked/of")
    public Result likedOf(@PathParam("id") long id) {
        Long userId = SecurityContextParam.getCurrentUserId();
        Double liked = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_PREFIX + id,userId.toString());
        return Result.success(liked == null ? false : true);
    }
    @GetMapping("liked/of/all")
    public Result likedOfAll(@PathParam("id") long id) {
        return Result.success(stringRedisTemplate.opsForZSet().range(BLOG_LIKED_PREFIX + id, 0, -1));
    }

    @GetMapping("/liked/of/user")
    public Result likedHot(@PathParam("id") long id) {
        Set<String> set= stringRedisTemplate.opsForZSet().range(BLOG_LIKED_PREFIX + id, 0, 2);
        if (CollectionUtil.isEmpty(set)){
            return Result.error(null);
        }
        List<Long> ids = set.stream().map(Long::parseLong).toList();
        List<User> userList = userService.listByIds(ids);
        return Result.success(userList);
    }
    /**
     * blog收件箱
     * @return
     */
    @GetMapping("/follow/of/all")
    public Result follow(@RequestParam(required = false) Long max, Long offset){
        if (max == null){
            max = System.currentTimeMillis();
        }
        Long userId = SecurityContextParam.getCurrentUserId();
        Set<ZSetOperations. TypedTuple<String>> result = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                BLOG_FOLLOW_PREFIX+userId, 0,max,offset,10);
        if (CollectionUtil.isEmpty(result)){
            return Result.success(null);
        }
        List<Long> ids = new ArrayList<>(result.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : result) {
            ids.add(Long.parseLong(typedTuple.getValue()));
            Long time = typedTuple.getScore().longValue();
            if (minTime == time){
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        List<Blog> blogList = blogService.listByIds(ids);
        ScrollResult next = new ScrollResult();
        next.setList(blogList);
        next.setMinTime(minTime);
        next.setOffset(Long.valueOf(os));
        return Result.success(next);
    }

}
