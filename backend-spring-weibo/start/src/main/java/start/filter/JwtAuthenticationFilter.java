package start.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证拦截过滤器
 *
 * 职责：拦截未登录的请求
 * 执行时机：在 UserRefreshRequestFilter / EmployeeRefreshRequestFilter 之后
 *
 * 注意：必须跳过公共路径（登录、注册等），否则未登录请求也会被拦截
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * 跳过不需要认证的公共路径
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // OPTIONS 请求（CORS 预检）直接放行
        if (request.getMethod().equalsIgnoreCase(HttpMethod.OPTIONS.name())) {
            return true;
        }
        // 公共路径 — 与 SecurityConfig 中 permitAll() 保持同步
        return path.equals("/user/register")
                || path.equals("/user/login")
                || path.equals("/user/logout")
                || path.equals("/employee/login")
                || path.equals("/employee/logout")
                || path.equals("/login/code")
                || path.equals("/login/byEmail")
                || path.startsWith("/img/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("未登录请求被拦截: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // 已认证：放行到后续过滤器 / Controller
        filterChain.doFilter(request, response);
    }
}
