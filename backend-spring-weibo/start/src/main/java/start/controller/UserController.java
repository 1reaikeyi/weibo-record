package start.controller;

import common.constant.JwtConstant;
import common.properties.JwtProperties;
import common.result.Result;
import common.util.JwtUtil;
import start.security.SecurityContextParam;
import model.entity.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import service.UserService;
import start.aspect.Info;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import start.security.LoginPrincipal;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
    @Autowired
    private AuthenticationManager authenticationManager; // 注入认证管理器
    
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
        // 使用 AuthenticationManager 进行认证（新方案）
        // Security 会自动调用 UserServiceImpl.loadUserByUsername() 查询用户
        // 并使用 BCryptPasswordEncoder.matches() 验证密码
        UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(userName, password);
        Authentication authentication = authenticationManager.authenticate(authRequest);
        
        // 认证成功后，查询用户完整信息（用于获取用户ID）
        User user = userService.findByUsername(userName);
        
        // 构建包含用户ID的认证对象并设置到 SecurityContext
        Map<String,Object> map = new HashMap<>();
        map.put(JwtConstant.ID, user.getId());
        map.put(JwtConstant.NAME, user.getUserName());
        UsernamePasswordAuthenticationToken authenticated = new UsernamePasswordAuthenticationToken(
                new LoginPrincipal(user.getId(), user.getUserName()),                           // principal - 用户ID（便于后续获取）
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authenticated);
        
        // 生成 JWT Token
        String token = JwtUtil.createJWT(jwtProperties.getSecretKey(), jwtProperties.getTtlMillis(), map);
        // 将 Token 存入 Redis（白名单策略，logout 时删除）
        stringRedisTemplate.opsForValue().set("weibo:"+user.getId(), token, jwtProperties.getTtlMillis(), TimeUnit.SECONDS);
        
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
        
        // 从 Spring Security Context 获取当前登录用户ID（新方案）
        // 使用 SecurityUtil 工具类，替代原有的 ThreadLocalContextHolder.get()
        Long userId = SecurityContextParam.getCurrentUserId();
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
        stringRedisTemplate.delete("weibo:"+ userId);
        return Result.success("更新密码成功::"+userId);
    }

    /**
     * 用户登出
     * 
     * 删除 Redis 中的 Token（白名单策略，删除后 Token 失效）
     * 
     * @return 结果
     */
    @Info(desc = "用户登出")
    @PostMapping("/logout")
    public Result logout(){
        // 获取当前登录用户ID
        Long userId = SecurityContextParam.getCurrentUserId();
        if (userId == null) {
            return Result.error("未登录");
        }
        
        // 删除 Redis 中的 Token（白名单策略）
        // 删除后，后续请求携带该 Token 将无法通过 JwtAuthenticationFilter 的验证
        stringRedisTemplate.delete("weibo:" + userId);
        
        log.info("用户登出成功，用户ID: {}", userId);
        return Result.success("登出成功");
    }
}