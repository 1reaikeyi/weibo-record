package start.controller.worker;

import static common.constant.RedisPrefixContant.WEIBO_USER_AUTHHEADER_PREFIX;

import common.constant.JwtConstant;
import common.properties.JwtProperties;
import common.result.Result;
import common.util.JwtUtil;
import model.dto.PassworEditEdDTO;
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
        User checkUser = userService.findByUsername(user.getUsername());
        if(checkUser != null){
            return Result.error("用户名已存在");
        }
        if (user.getPassword() == null){
            user.setPassword("123456");
        }
        User newUser = User.builder()
                .username(user.getUsername())
                .password(passwordEncoder.encode(user.getPassword()))
                .nickName(user.getNickName()).email(user.getEmail()).userPic(user.getUserPic())
                .build();
        userService.save(newUser);
        return Result.success("注册成功::"+newUser.getId());
    }
    
    /**
     * 用户登录
     * 
     * @param username 用户名
     * @param password 密码
     * @return 结果
     */
    @Info(desc = "用户登录")
    @PostMapping("/login")
    public Result login(String username, @Pattern(regexp = "^\\S{5,16}$") String password){
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                "user:" + username, password);
        Authentication authentication = authenticationManager.authenticate(usernamePasswordAuthenticationToken);
        if (!authentication.isAuthenticated()){
            return Result.error("用户名或密码错误");
        }
        // 认证成功后，查询用户完整信息
        User user = userService.findByUsername(username);
        Map<String,Object> map = new HashMap<>();
        map.put(JwtConstant.USER_ID, user.getId());
        map.put(JwtConstant.USER_NAME, user.getUsername());
        map.put(JwtConstant.TYPE, "user");
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                new LoginPrincipal(user.getId(), user.getUsername()),
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        System.out.println("authenticationToken = " + authenticationToken);
        /**
         * UsernamePasswordAuthenticationToken
         * [Principal=LoginPrincipal(id=1, username=张三), Credentials=[PROTECTED], Authenticated=true, Details=null,
         * Granted Authorities=[ROLE_USER]]
         */
        // 构建包含用户ID的认证对象并设置到 SecurityContext
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        String token = JwtUtil.createJWT(jwtProperties.getSecretKey(), jwtProperties.getTtlMillis(), map);
        stringRedisTemplate.opsForValue().set(WEIBO_USER_AUTHHEADER_PREFIX + user.getId(), token, jwtProperties.getTtlMillis(), TimeUnit.SECONDS);
        return Result.success(token);
    }
    @Info(desc = "用户登出")
    @PostMapping("/logout")
    public Result logout(){
        // 获取当前登录用户ID
        Long userId = SecurityContextParam.getCurrentUserId();
        if (userId == null) {
            return Result.error("未登录");
        }
        //删除
        stringRedisTemplate.delete(WEIBO_USER_AUTHHEADER_PREFIX + userId);
        //删除线程
        SecurityContextHolder.clearContext();
        return Result.success("logout");
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
        return Result.success("updateUser::"+user.getId());
    }

    /**
     * 更新用户密码
     *
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @PatchMapping("/updatePwd")
    public Result updatePassword(@RequestBody PassworEditEdDTO passworEditEdDTO){
        String oldPassword = passworEditEdDTO.getOldPassword();
        String newPassword = passworEditEdDTO.getNewPassword();
        String checkPassword = passworEditEdDTO.getConfirmPassword();

        // 参数校验
        if(oldPassword == null || newPassword == null || checkPassword == null){
            return Result.error("缺少必要参数");
        }
        // 新密码与确认密码一致性校验
        if(!newPassword.equals(checkPassword)){
            return Result.error("新密码与确认密码不一致");
        }

        // 从 Spring Security Context 获取当前登录用户ID
        Long userId = SecurityContextParam.getCurrentUserId();
        User user = userService.getById(userId);
        if(user == null){
            return Result.error("用户不存在");
        }

        // 使用 BCrypt 的 matches 方法验证旧密码（单向哈希无法解密，只能比对）
        if(!passwordEncoder.matches(oldPassword, user.getPassword())){
            return Result.error("旧密码不正确");
        }

        // 使用 BCrypt 加密新密码
        user.setPassword(passwordEncoder.encode(newPassword));
        userService.updateById(user);

        // 清除旧的登录令牌，强制重新登录
        stringRedisTemplate.delete(WEIBO_USER_AUTHHEADER_PREFIX + userId);
        return Result.success("重新登录::"+userId);
    }

}