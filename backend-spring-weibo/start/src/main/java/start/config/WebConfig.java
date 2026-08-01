package start.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类 - 配置资源处理器
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    /**
     * 配置静态资源处理器
     * 访问路径: http://localhost:8080/img/xxx
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        //img
        registry.addResourceHandler("/img/**")
                .addResourceLocations("file:img/");
        // 保留默认映射
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }

}