package start.controller.admin;

import common.constant.JwtConstant;
import common.properties.JwtProperties;
import common.result.Result;
import common.util.JwtUtil;
import jakarta.validation.constraints.Pattern;
import model.entity.Employee;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.EmployeeService;
import start.security.LoginPrincipal;
import start.security.SecurityContextParam;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static common.constant.RedisPrefixContant.WEIBO_EMP_AUTHHEADER_PREFIX;

@RestController
@RequestMapping("/admin/employee")
public class EmployeeController {

    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private EmployeeService employeeService;

    @PostMapping("/login")
    public Result login(String username, @Pattern(regexp = "^\\S{5,16}$") String password) {
        try {
            /**
             *   ProviderManager.authenticate(token)
             *      │  遍历它注册的所有 AuthenticationProvider
             *      │  对每个 provider 调 provider.supports(UsernamePasswordAuthenticationToken.class)
             *      │
             *      ├─ 你项目里只有一个 Provider：MultiLoginAuthenticationProvider
             *      │     它的 supports() 对 UsernamePasswordAuthenticationToken 返回 true → 命中
             *      │
             *      ▼
             *   MultiLoginAuthenticationProvider.authenticate(token)   ← 真正干活的是这个
             */
            UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                    "emp:" + username, password);
            Authentication authentication = authenticationManager.authenticate(usernamePasswordAuthenticationToken);
            if (!authentication.isAuthenticated()){
                return Result.error("用户名或密码错误");
            }
            Employee employee = employeeService.findByUsername(username);

            Map<String, Object> map = new HashMap<>();
            map.put(JwtConstant.EMP_ID, employee.getId());
            map.put(JwtConstant.EMP_NAME, employee.getUsername());
            map.put(JwtConstant.TYPE, "emp");
            /**
             *
             ┌────────────────────────────────────────────┬───────────────────────────────────────┐
             │                    构造                    │             authenticated             │
             ├────────────────────────────────────────────┼───────────────────────────────────────┤
             │ (principal, credentials) 2 参 ← 你用的这个 │ false（未认证），只是装了个"登录请求" │
             ├────────────────────────────────────────────┼───────────────────────────────────────┤
             │ (principal, credentials, authorities) 3 参 │ true（已认证），权限也带上            │
             └────────────────────────────────────────────┴───────────────────────────────────────┘
             */
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    new LoginPrincipal(employee.getId(),employee.getUsername()),
                    null,
                    authentication.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            String token = JwtUtil.createJWT(jwtProperties.getSecretKey(), jwtProperties.getTtlMillis(), map);
            stringRedisTemplate.opsForValue().set(
                    WEIBO_EMP_AUTHHEADER_PREFIX + employee.getId(), token, jwtProperties.getTtlMillis(), TimeUnit.SECONDS);
            return Result.success(token);
        } catch (BadCredentialsException e) {
            return Result.error("用户名或密码错误");
        }
    }

    @PostMapping("/logout")
    public Result logout() {
        Long empId = SecurityContextParam.getCurrentUserId();
        if (empId == null) {
            return Result.error("未登录");
        }
        stringRedisTemplate.delete(WEIBO_EMP_AUTHHEADER_PREFIX + empId);
        SecurityContextHolder.clearContext();
        return Result.success("logout");
    }
}
