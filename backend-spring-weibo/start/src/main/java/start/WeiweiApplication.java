package start;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 启动类 - Spring Boot应用入口
 */
@SpringBootApplication(scanBasePackages = {"start", "service", "common"})
@MapperScan("mapper")
@EnableTransactionManagement
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableConfigurationProperties
@Slf4j
public class WeiweiApplication {
    public static void main(String[] args) {
        SpringApplication.run(WeiweiApplication.class, args);
        log.info("---匹配成功");
    }
}