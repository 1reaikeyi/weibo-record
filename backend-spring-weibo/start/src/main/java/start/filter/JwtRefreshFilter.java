package start.filter;

import common.constant.JwtConstant;
import common.properties.JwtProperties;
import common.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JWT刷新与验证过滤器（第一个）
 * 
 * 职责：
 * 1. 提取请求头中的 Token
 * 2. 验证 JWT 有效性
 * 3. 刷新 Redis 中 Token 的过期时间（滑动过期策略）
 * 4. 设置 SecurityContext
 */
@Slf4j
public class JwtRefreshFilter extends OncePerRequestFilter {

    private final JwtProperties jwtProperties;
    private final StringRedisTemplate stringRedisTemplate;

    public JwtRefreshFilter(JwtProperties jwtProperties, StringRedisTemplate stringRedisTemplate) {
        this.jwtProperties = jwtProperties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String token = extractToken(request);

            if (token == null) {
                filterChain.doFilter(request, response);
                return;
            }

            Map<String, Object> claims = JwtUtil.parseJWT(jwtProperties.getSecretKey(), token);
            String currentId = claims.get(JwtConstant.ID).toString();
            Long userId = Long.parseLong(currentId);

            String standardToken = stringRedisTemplate.opsForValue().get("weibo:" + userId);
            if (!token.equals(standardToken)) {
                log.warn("Token验证失败，可能已注销或被篡改, 用户ID: {}", userId);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userId,
                    token,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            try {
                stringRedisTemplate.expire("weibo:" + userId, jwtProperties.getTtlMillis(), TimeUnit.SECONDS);
                log.debug("Token过期时间已刷新，用户ID: {}", userId);
            } catch (Exception e) {
                log.warn("刷新Token过期时间失败: {}", e.getMessage());
            }

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.warn("JWT处理失败: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
