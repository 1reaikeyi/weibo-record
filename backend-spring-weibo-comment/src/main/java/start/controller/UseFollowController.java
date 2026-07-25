package start.controller;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import common.ThreadLocalContext.ThreadLocalParam;
import common.result.Result;
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
    private static final String KEY = "follow";

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
            stringRedisTemplate.opsForSet().add(KEY+userId, followUserId.toString());
        userFollowService.save(userFollow);
        }
        if(!ifFollow){
            userFollowService.remove(new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getFollowUserId,userId)
                    .eq(UserFollow::getFollowUserId,followUserId));
            stringRedisTemplate.opsForSet().remove(KEY+userId, followUserId.toString());
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
        Set<String> comomSet = stringRedisTemplate.opsForSet().intersect(KEY + followId, KEY + userId);
        List<User> userList = comomSet.stream()
                .map(s -> userService.getById(Long.parseLong(s))).toList();
        return Result.success(userList);
    }
}
