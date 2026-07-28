package start.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 注册 BCryptPasswordEncoder 为 Spring Bean
     * BCrypt 是单向哈希算法，内置随机盐，每次加密结果不同
     * 使用 matches(rawPassword, encodedPassword) 方法验证密码，而非直接解密
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. 关闭 CSRF (如果是前后端分离或不使用表单登录，建议关闭)
                .csrf(csrf -> csrf.disable())

                // 2. 配置授权规则
                .authorizeHttpRequests(auth -> auth
                        // 这一行表示：所有请求 都不需要认证，直接放行
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}
