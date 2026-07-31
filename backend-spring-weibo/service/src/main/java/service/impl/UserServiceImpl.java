package service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import common.properties.EmailProperties;
import model.entity.User;
import mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 用户服务实现类 - 实现用户相关业务逻辑
 * 
 * 同时实现 UserDetailsService 接口，供 Spring Security 登录认证使用
 * Security 在调用 authenticationManager.authenticate() 时自动调用 loadUserByUsername()
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService{
    @Autowired
    private JavaMailSender mailSender;
    @Autowired
    private PasswordEncoder passwordEncoder; // 注入密码加密器 Bean
    @Autowired
    private EmailProperties emailProperties;
    @Override
    public User findByUsername(String username) {
        return this.lambdaQuery()
                .eq(User::getUsername, username)
                .one();
    }

    @Override
    public User matchUser(String userName, String password) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, userName);
        User checkUser = this.getOne(queryWrapper);
        if (checkUser == null) {
            throw new RuntimeException("用户名或密码错误");
        }
        // 使用注入的 PasswordEncoder Bean 验证密码
        // matches(rawPassword, encodedPassword)：将明文与加密后的密码比对
        if (!passwordEncoder.matches(password, checkUser.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        return checkUser;
    }


    @Override
    public User matchEmail(String email) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getEmail, email);
        User checkUser = this.getOne(queryWrapper);
        if (checkUser == null) {
            throw new RuntimeException("用户不存在");
        }
        return checkUser;
    }

    /**
     * 发送简单文本邮件
     * @param to 收件人
     * @param subject 主题
     * @param content 内容
     */
    @Override
    public Boolean sendEmail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailProperties.getUsername());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            throw new RuntimeException(e);
        }
    }


}