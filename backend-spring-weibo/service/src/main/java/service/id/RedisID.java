package service.id;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class RedisID {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final static long BEGIN_TIME = 1577836800L;
    // 31位时间最大允许值
    private static final long MAX_TIME_OFFSET = (1L << 31) - 1;
    // 32位序号最大值
    private static final long MAX_SEQ = 0xFFFFFFFFL;

    public long createId(String prefix) {
        ZonedDateTime utcNow = ZonedDateTime.now(ZoneOffset.UTC);
        long nowSeconds = utcNow.toEpochSecond();
        long timestamp = nowSeconds - BEGIN_TIME;

        // 校验1：时间偏移不能超过31位容量
        if (timestamp > MAX_TIME_OFFSET) {
            throw new RuntimeException("时间偏移超出31位上限，需重置基准BEGIN_TIME");
        }

        String date = utcNow.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        String key = "icr:" + prefix + ":" + date;
        long count = stringRedisTemplate.opsForValue().increment(key);

        // 校验2：序号不能超过32位最大值
        if (count > MAX_SEQ) {
            throw new RuntimeException("当日并发序号超出32位上限");
        }

        return timestamp << 32 | count;
    }

//    public static void main(String[] args) {
//        LocalDateTime now = LocalDateTime.of(2020,1,1,0,0,0);
//        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
//        System.out.println(nowSeconds);
//
//    }

}
