package service.lock;

import org.springframework.core.io.ClassPathResource;
import cn.hutool.core.lang.UUID;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@NoArgsConstructor
public class RedisLock implements ILock{
    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final String KEY_PREFIX = "lock:";
    private static final String VALUE_PREFIX = UUID.randomUUID().toString() + ":";
    private static final DefaultRedisScript<Long> REDISSCRIPT = new DefaultRedisScript<>();
    static {
        // 设置Lua脚本路径，注意文件名是 redis-unlock.lua
        REDISSCRIPT.setLocation(new ClassPathResource("redis-unlock.lua"));
        REDISSCRIPT.setResultType(Long.class);
    }
    /**
     * 获取锁
     * @param timeoutSec
     * @return
     */
    @Override
    public boolean getLocked(long timeoutSec) {
        String key = KEY_PREFIX + name;
        Long id = Thread.currentThread().getId();
        String value = VALUE_PREFIX+id;
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key,value,timeoutSec, TimeUnit.SECONDS);
        return Optional.ofNullable(success).orElse(false);
    }

    /**
     * 2.0释放锁
     */
    @Override
    public void unlock() {
        String key = KEY_PREFIX + name;
        Long id = Thread.currentThread().getId();
        String value = VALUE_PREFIX+id;
        stringRedisTemplate.execute(REDISSCRIPT, List.of(key),value);
    }

    /**
     * 1.0 释放锁
     @Override
     public void unlook() {
     String key = KEY_PREFIX + name;
     String value = stringRedisTemplate.opsForValue().get(key);
     String validtor_id = VALUE_PREFIX + Thread.currentThread().getId();
     if (value.equals(validtor_id)) {
     stringRedisTemplate.delete(KEY_PREFIX+name);
     }
     }
     */


}
