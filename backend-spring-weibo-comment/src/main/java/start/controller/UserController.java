package start.controller;

import common.constant.JwtConstant;
import common.properties.JwtProperties;
import common.result.Result;
import common.util.JwtUtil;
import common.ThreadLocalContext.ThreadLocalContextHolder;
import model.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import service.UserService;
import start.aspect.Info;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 用户管理控制器
 * 
 * @author Smart-doc
 * @since 1.0.0
 */

@RestController
@RequestMapping("/user")
@Validated
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder; // 注入密码加密器 Bean
    
    /**
     * 用户注册
     * 
     * @param user 用户信息
     * @return 结果
     */
    @PostMapping("/register")
    public Result register(@RequestBody User user){
        User checkUser = userService.findByUsername(user.getUserName());
        if(checkUser != null){
            return Result.error("用户名已存在");
        }
        if (user.getPassword() == null){
            user.setPassword("123456");
        }
        User newUser = User.builder()
                .userName(user.getUserName())
                // 使用注入的 PasswordEncoder Bean 进行密码加密
                // BCrypt 是单向哈希算法，每次加密结果不同（内置随机盐）
                .password(passwordEncoder.encode(user.getPassword()))
                .nickName(user.getNickName()).email(user.getEmail()).userPic(user.getUserPic())
                .build();
        userService.save(newUser);
        return Result.success("注册成功::"+newUser.getId());
    }
    
    /**
     * 用户登录
     * 
     * @param userName 用户名
     * @param password 密码
     * @return 结果
     */
    @Info(desc = "用户登录")
    @PostMapping("/login")
    public Result login(String userName, @Pattern(regexp = "^\\S{5,16}$") String password){
        User user = userService.matchUser(userName, password);
        Map<String,Object> map = new HashMap<>();
        map.put(JwtConstant.ID, user.getId());
        map.put(JwtConstant.NAME, user.getUserName());
        ThreadLocalContextHolder.set(map);
        String token = JwtUtil.createJWT(jwtProperties.getSecretKey(), jwtProperties.getTtlMillis(), map);
        stringRedisTemplate.opsForValue().set("bigevent:"+user.getId(), token, jwtProperties.getTtlMillis(), TimeUnit.SECONDS);
        return Result.success(token);
    }

    /**
     * 获取用户信息
     * 
     * @param id 用户ID
     * @return 结果
     */
    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id){
        return Result.success(userService.getById(id));
    }
       
    /**
     * 更新用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    @PutMapping
    public Result updateUser(@RequestBody User user){
        userService.updateById(user);
        return Result.success("更新成功::"+user.getId());
    }

    /**
     * 更新用户密码
     * 
     * @param params 参数（包含 old_pwd, new_pwd, check_pwd）
     * @return 结果
     */
    @PatchMapping("/updatePwd")
    public Result updatePassword(@RequestBody Map<String,String> params){
        String oldPassword = params.get("old_pwd");
        String newPassword = params.get("new_pwd");
        String checkPassword = params.get("check_pwd");
        
        // 参数校验
        if(oldPassword == null || newPassword == null || checkPassword == null){
            return Result.error("缺少必要参数");
        }
        // 新密码与确认密码一致性校验
        if(!newPassword.equals(checkPassword)){
            return Result.error("新密码与确认密码不一致");
        }
        
        // 从 ThreadLocal 获取当前登录用户信息
        Map<String, Object> userInfo = ThreadLocalContextHolder.get();
        Long userId = (Long) userInfo.get(JwtConstant.ID);
        User user = userService.getById(userId);
        if(user == null){
            return Result.error("用户不存在");
        }
        
        // 使用 BCrypt 的 matches 方法验证旧密码（单向哈希无法解密，只能比对）
        if(!passwordEncoder.matches(oldPassword, user.getPassword())){
            return Result.error("旧密码不正确");
        }
        
        // 使用 BCrypt 加密新密码（替换原有的 MD5 加密方式）
        user.setPassword(passwordEncoder.encode(newPassword));
        userService.updateById(user);
        
        // 清除旧的登录令牌，强制重新登录
        stringRedisTemplate.delete("bigevent:"+ userId);
        return Result.success("更新密码成功::"+userId);
    }
}