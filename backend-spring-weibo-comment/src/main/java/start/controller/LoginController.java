package start.controller;

import common.ThreadLocalContext.ThreadLocalContextHolder;
import common.constant.JwtConstant;
import common.properties.JwtProperties;
import common.result.Result;
import common.util.JwtUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import model.entity.User;
import model.entity.VoucherOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.UserService;
import service.lock.ILock;
import service.lock.RedisLock;
import start.aspect.Info;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("login")
@Slf4j
public class LoginController {
    @Autowired
    private UserService userService;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String CODE_PREFIX = "code:";
    private static final String CODE_STREAM = "valid:code:stream";
    private static final String CODE_STREAM_GROUP = "group";

    private static final ExecutorService CODE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "code-handler");
        t.setDaemon(true);
        return t;
    });
    /**
     * 初始化方法 - 启动异步订单处理线程
     */
    @PostConstruct
    public void init() {
        try {
            stringRedisTemplate.opsForStream().createGroup(CODE_STREAM, CODE_STREAM_GROUP);
            log.info("Redis Stream消费组"+CODE_STREAM_GROUP+"创建成功");
        } catch (Exception e) {
            log.info("二次确认:Redis Stream消费组"+CODE_STREAM_GROUP+"创建成功");
        }
        CODE_EXECUTOR.submit(new LoginController.HandleCodeTask());
    }

    @PreDestroy
    public void destroy() {
        CODE_EXECUTOR.shutdown();
        try {
            // 等待10秒让未处理的订单完成
            if (!CODE_EXECUTOR.awaitTermination(10, TimeUnit.SECONDS)) {
                CODE_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            CODE_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }


    /**
     * 发送验证码
     * @param email 邮箱地址
     * @return 结果
     */
    @Info(desc = "发送验证码")
    @PostMapping("/code")
    public Result sendCode(@Email String email) {
        String secret = "0123456789";
        StringBuilder codeBuilder = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 4; i++) {
            int index = random.nextInt(secret.length());
            codeBuilder.append(secret.charAt(index));
        }
        String code = codeBuilder.toString();
        stringRedisTemplate.opsForValue().set( CODE_PREFIX+ email, code, 10, TimeUnit.MINUTES);
        // XADD 命令：发送消息
        Map<String, String> message = new HashMap<>();
        message.put("code", code);
        message.put("email", email);
        stringRedisTemplate.opsForStream().add(CODE_STREAM, message);
        return Result.success("验证码："+code);
    }




    /**
     * 邮箱登录
     *
     * @param email 邮箱地址
     * @param code 验证码
     * @return 结果
     */
    @Info(desc = "邮箱登录")
    @PostMapping("byEmail")
    public Result loginByEmail(String email, String code){
        String standard_code = stringRedisTemplate.opsForValue().get(CODE_PREFIX+email);
        if(standard_code == null){
            return Result.error("验证码获取失败");
        }
        User user = userService.matchEmail(email);
        if(standard_code.equals(code)){
            Map<String,Object> map = new HashMap<>();
            map.put(JwtConstant.ID, user.getId());
            map.put(JwtConstant.NAME, user.getUserName());
            ThreadLocalContextHolder.set(map);
            String token = JwtUtil.createJWT(jwtProperties.getSecretKey(), jwtProperties.getTtlMillis(), map);
            stringRedisTemplate.opsForValue().set("bigevent:"+ user.getId(), token, jwtProperties.getTtlMillis(), TimeUnit.SECONDS);
            return Result.success(token);
        }
        return Result.error("验证失败");
    }


    private class HandleCodeTask implements Runnable {
        @Info(desc = "--异步处理")
        @Override
        public void run() {
            while (true) {

//                XREADGROUP GROUP g1 c1 count 1 BLOCK 0 STREAMS STREAM_KEY >
                try {
                    List<MapRecord<String,Object,Object>> messageList = stringRedisTemplate.opsForStream().read(
                            Consumer.from(CODE_STREAM_GROUP,UUID.randomUUID().toString()),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(10)),
                            StreamOffset.create(CODE_STREAM, ReadOffset.lastConsumed()));
                    if (messageList == null || messageList.isEmpty()) {
                        continue;
                    }
                    MapRecord<String,Object,Object> record = messageList.get(0);
                    Map<Object,Object> map = record.getValue();
                    String code = map.get("code").toString();
                    String email = map.get("email").toString();
                    log.info("发送给"+email +"::"+ code);
                    userService.sendEmail(email,"Hi Here's your next launch code!",
                            "Continue signing up for our by entering the code below::"+code);
                    log.info("邮件发送成功,发送给"+email +"success::"+ code);
                    stringRedisTemplate.opsForStream().acknowledge(CODE_STREAM,CODE_STREAM_GROUP,record.getId());
                } catch (Exception e) {
                    // 线程被中断，退出循环
                    Thread.currentThread().interrupt();
                    break;
                }

            }
        }
    }

}