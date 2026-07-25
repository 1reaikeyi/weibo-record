package start.config;

import org.springframework.web.servlet.config.annotation.CorsRegistry;
import start.interceptor.LoginInterceptor;
import start.interceptor.ReLoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类 - 配置拦截器和资源处理器
 */
@Configuration
public class WebConfigConfiguration implements WebMvcConfigurer {
    @Autowired
    private LoginInterceptor loginInterceptor;
    @Autowired
    private ReLoginInterceptor reLoginInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册登录拦截器，排除注册和登录接口
        registry.addInterceptor(reLoginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/user/register", "/user/login", "/user/logout",
                        "/user/code", "/user/byEmail")
                .order(0);
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/user/register", "/user/login", "/user/logout",
                        "/user/code", "/user/byEmail")
                .order(1);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")  // 允许所有路径跨域
                .allowedOriginPatterns("*") // 允许所有域名
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的方法
                .allowedHeaders("*") // 允许的请求头
                .allowCredentials(true) // 允许携带 Cookie
                .maxAge(3600); // 预检请求缓存时间(秒)
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 访问路径: http://localhost:8080/img/xxx
        registry.addResourceHandler("/img/**")
                .addResourceLocations("file:img/");
    }
}