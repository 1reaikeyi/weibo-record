package start.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.BooleanUtil;
import common.ThreadLocalContext.ThreadLocalParam;
import common.result.Result;
import model.entity.Sign;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.SignService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/sign")
public class SignController {
    @Autowired
    private SignService signService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final String SIGN_DATE = "sign:";
    @PostMapping
    public Result createSign() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        Long userId = ThreadLocalParam.getUserId();
        String dateKey = now.format(formatter);
        String key = SIGN_DATE + userId + ":" + dateKey;
        Long day = Long.valueOf(now.getDayOfMonth() - 1);
        Boolean result = stringRedisTemplate.opsForValue().setBit(key,day,true);
        return Result.success(result == true ? "已签到" : "签到成功");
    }
    @PostMapping("/back")
    public Result backSign(String time) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate localDate = LocalDate.parse(time, formatter);

        Long userId = ThreadLocalParam.getUserId();
        DateTimeFormatter formatterKey = DateTimeFormatter.ofPattern("yyyy-MM");
        String date = localDate.format(formatterKey);
        String key = SIGN_DATE + userId + ":" + date;
        Long value = Long.valueOf(localDate.getDayOfMonth() - 1 );
        Boolean result = stringRedisTemplate.opsForValue().setBit(key,value,true);
        return Result.success(result == true ? "已补签" : "补签成功");
    }
    @PostMapping ("/count")
    public Result CountSign() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        Long userId = ThreadLocalParam.getUserId();
        String dateKey = now.format(formatter);
        String key = SIGN_DATE + userId + ":" + dateKey;
        // bitfield key get u8 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(now.getDayOfMonth()))
                        .valueAt(0));
        if (CollectionUtil.isEmpty(result)){
            return Result.success(0);
        }
        Long num10 = result.get(0);
        String num2 = Long.toBinaryString(num10);
        int count =0;
        for (int i = 0; i<num2.length(); i++){
            if ( '1' == num2.charAt(i) ){
                count++;
            }
        }
        return Result.success("签到::"+ count+",缺勤::"+(num2.length()-count));
    }

}
