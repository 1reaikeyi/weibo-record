package service;

import com.baomidou.mybatisplus.extension.service.IService;
import model.entity.User;

/**
 * 用户服务接口 - 定义用户相关业务操作
 */
public interface UserService extends IService<User> {
    User findByUsername(String username);
    User matchUser(String userName, String password);
    User matchEmail(String email);
    /**
     * 发送简单文本邮件
     * @param to 收件人
     * @param subject 主题
     * @param content 内容
     */
    Boolean sendEmail(String to, String subject, String content);

}