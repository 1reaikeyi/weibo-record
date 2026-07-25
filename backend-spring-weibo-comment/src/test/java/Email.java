import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import service.UserService;
import start.BigEventApplication;

@SpringBootTest(classes = BigEventApplication.class)
@Slf4j
public class Email {
    @Autowired
    private UserService userService;
    @Test
    public void test() {
        try {
            String email = "reaikeyi@qq.com";
            String code = "qq1123";
            userService.sendEmail(email,"这是你的启动代码","继续注册请输入以下代码:"+code);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
