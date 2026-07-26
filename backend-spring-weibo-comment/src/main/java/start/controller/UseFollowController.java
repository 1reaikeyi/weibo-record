package start.controller;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import common.ThreadLocalContext.ThreadLocalParam;
import common.result.Result;
import jakarta.websocket.server.PathParam;
import model.entity.User;
import model.entity.UserFollow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import service.UserFollowService;
import service.UserService;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/follow")
public class UseFollowController {
    @Autowired
    private UserFollowService userFollowService;
    @Autowired
    private UserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String FOLLOW_PREFIX = "follow:";

    /**
     * 关注
     * @param followUserId
     * @param ifFollow
     * @return
     */
    @PostMapping("/{id}/{ifFollow}")
    public Result useFollow(@PathVariable("id") Long followUserId, @PathVariable Boolean ifFollow) {
        Long userId = ThreadLocalParam.getUserId();
        if(ifFollow){
            UserFollow userFollow = UserFollow.builder()
                    .userId(userId)
                    .followUserId(followUserId)
                    .build();
            stringRedisTemplate.opsForSet().add(FOLLOW_PREFIX+userId, followUserId.toString());
        userFollowService.save(userFollow);
        }
        if(!ifFollow){
            userFollowService.remove(new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getFollowUserId,userId)
                    .eq(UserFollow::getFollowUserId,followUserId));
            stringRedisTemplate.opsForSet().remove(FOLLOW_PREFIX+userId, followUserId.toString());
        }
        return Result.success(followUserId + "::" + (BooleanUtil.isTrue(ifFollow) ? "关注" : "取关" ));
    }

    /**
     * 获取结果
     * @param followUserId
     * @return
     */
    @GetMapping("/{id}")
    public Result getUserFollow(@PathVariable("id") Long followUserId) {
        Long userId = ThreadLocalParam.getUserId();
        UserFollow userFollow = userFollowService.getOne(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getUserId,userId)
                .eq(UserFollow::getFollowUserId,followUserId));
        return Result.success(userFollow != null ? "关注" : "取关" );
    }

    /**
     * 共同关注
     * @param followId
     * @return
     */
    @GetMapping("/common/{id}")
    public Result getUserFollowCommon(@PathVariable("id") Long followId) {
        Long userId = ThreadLocalParam.getUserId();
        Set<String> commonSet = stringRedisTemplate.opsForSet().intersect(FOLLOW_PREFIX + followId, FOLLOW_PREFIX + userId);
        if (CollectionUtil.isEmpty(commonSet)){
            return Result.error(null);
        }

        List<Long> ids = commonSet.stream().map(s -> Long.parseLong(s)).toList();
        List<User> userList = userService.listByIds(ids);
        return Result.success(userList);
    }

    /**
     * 获得对方blog
     * @param id
     * @return
     */
    @GetMapping("/allBlog")
    public Result follow(@PathParam("id") Long id) {
        return Result.success();
    }
}
