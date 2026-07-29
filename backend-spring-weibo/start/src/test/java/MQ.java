import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import start.WeiweiApplication;

import java.time.Duration;
import java.util.*;

@SpringBootTest(classes = WeiweiApplication.class)
@Slf4j
public class MQ {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Test
    public void test() {
        List<MapRecord<String,Object,Object>> messageList = stringRedisTemplate.opsForStream().read(
                Consumer.from("g1","c1"),
                StreamReadOptions.empty().count(1).block(Duration.ofSeconds(20)),
                StreamOffset.create("s1", ReadOffset.lastConsumed()));

        if (messageList == null || messageList.isEmpty()) {
            System.out.println("没有获取到消息");
            return;
        }

        MapRecord<String,Object,Object> record = messageList.get(0);

        // 第一个泛型 String：stream key
        String streamKey = record.getStream();
        // 第二个泛型 Object：消息ID
        Object msgId = record.getId();
        // 第三个泛型 Object：消息内容 Map
        Object bodyObj = record.getValue();
        Map<Object,Object> map = (Map<Object, Object>) bodyObj;
        System.out.println("【第1泛型-Stream队列名】streamKey = " + streamKey);
        System.out.println("【第2泛型-消息ID】msgId = " + msgId);
        System.out.println("【第3泛型-消息KV内容】map = " + map);
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            System.out.println("key：" + entry.getKey() + " , value：" + entry.getValue());
        }


    }
}
