import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import start.BigEventApplication;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

public class GETTest {
    @Test
    public void test() {
        System.out.println("UUID.randomUUID() " + UUID.randomUUID().toString());
//        判断是否是假
        System.out.println("BooleanUtil.isFalse(1==1) = " + BooleanUtil.isFalse(1==1));
//        判断是否是真
        System.out.println("BooleanUtil.isTrue(1==1) = " + BooleanUtil.isTrue(1==1));
        Set<String> set = new HashSet<>();
        set.add("1");
        set.add("2");
        set.add("3");
        for (int i = 0; i < set.size(); i++) {
            System.out.println(i);
        }
    }
    @Test
    public void test2() {
        int year = 2026;
        int month = 12;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate localDate = LocalDate.parse(year+"-"+month+"-"+"01", formatter);
        int day = localDate.lengthOfMonth();
        System.out.println("days = " + day);
    }
}
