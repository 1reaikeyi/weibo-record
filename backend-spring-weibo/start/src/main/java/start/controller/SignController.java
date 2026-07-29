package start.controller;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import common.result.Result;

import model.entity.Sign;
import model.dto.TimeDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import service.SignService;
import start.security.SecurityContextParam;

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
        Long userId = SecurityContextParam.getCurrentUserId();
        String key = SIGN_DATE + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        Long day = Long.valueOf(now.getDayOfMonth() - 1);
        Boolean result = stringRedisTemplate.opsForValue().setBit(key,day,true);
        return Result.success(result == true ? "已签到" : "签到成功");
    }
    @PutMapping("/back")
    public Result backSign(@RequestBody TimeDTO time) {
        LocalDate localDate = LocalDate.parse(time.getTime(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Long userId = SecurityContextParam.getCurrentUserId();
        String key = SIGN_DATE + userId + ":" + localDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        Long value = Long.valueOf(localDate.getDayOfMonth() - 1 );
        Boolean result = stringRedisTemplate.opsForValue().setBit(key,value,true);
        return Result.success(result == true ? "已补签" : "补签成功");
    }
    @PostMapping ("/count/day")
    public Result CountSign() {
        LocalDateTime now = LocalDateTime.now();
        Long userId = SecurityContextParam.getCurrentUserId();
        String key = SIGN_DATE + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        // bitfield key get u8 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(now.getDayOfMonth()))
                        .valueAt(0));
        if (CollectionUtil.isEmpty(result)){
            return Result.success(0);
        }
        Long num10 = result.get(0);
        long signedDays = 0;
        for (int i = 0; i < now.getDayOfMonth(); i++){
            if ((num10 & 1) == 1){
                signedDays++;
            }
            num10 = num10 >>>1;
        }
        long unSignedDays = now.getDayOfMonth() - signedDays;
        return Result.success("签到::"+ signedDays+",缺勤::" + unSignedDays);
    }
    @PostMapping("/count/month")
    public Result Sign(@RequestBody TimeDTO time) {
        LocalDate localDate = LocalDate.parse(time.getTime()+"-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Long userId = SecurityContextParam.getCurrentUserId();
        String key = SIGN_DATE + userId + ":" + time.getTime();
        // bitfield key get u8 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(localDate.lengthOfMonth()))
                .valueAt(0));
        if (CollectionUtil.isEmpty(result)){
            return Result.success(0);
        }
        Long num10 = result.get(0);
        long signedDays = Long.bitCount(num10);
        long unSignedDays = localDate.lengthOfMonth() - signedDays;
        Sign sign = Sign.builder()
                .userId(userId).signed(signedDays).notSigned(unSignedDays)
                .year(Long.valueOf(localDate.getYear()))
                .month(Long.valueOf(localDate.getMonthValue()))
                .build();
        signService.remove(new LambdaQueryWrapper<Sign>()
                .eq(Sign::getYear,Long.valueOf(localDate.getYear()))
                .eq(Sign::getMonth,Long.valueOf(localDate.getMonthValue())));
        signService.save(sign);
        return Result.success(sign);
    }
    @GetMapping("/{year}/{month}")
    public Result SignCount(@PathVariable Long year,@PathVariable Long month) {
        Sign sign = signService.getOne(new LambdaQueryWrapper<Sign>()
                .eq(Sign::getYear, year)
                .eq(Sign::getMonth,month));
        return Result.success(sign);
    }

}
