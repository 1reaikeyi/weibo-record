package start.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import model.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import service.UserService;

import java.util.Collections;
@Service
@Slf4j
public class LoginUserService implements UserDetailsService {
    @Autowired
    private UserService userService;
    /**
     * Spring Security 用户详情加载方法
     *
     * Security 在调用 authenticationManager.authenticate() 时自动调用此方法
     * 通过用户名查询数据库，返回 Security 的 User 对象（包含密码和权限）
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // userService的 findByUsername() 方法查询用户
        User user = userService.findByUsername(username);

        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        // 返回 Security 的 User 对象（注意：这里是 org.springframework.security.core.userdetails.User）
        return new org.springframework.security.core.userdetails.User(
                user.getUserName(),           // 用户名
                user.getPassword(),           // 加密后的密码（Security 会自动比对）
                Collections.singletonList(    // 权限列表（当前默认给普通用户角色）
                        new SimpleGrantedAuthority("ROLE_USER")
                )
        );
    }

}
