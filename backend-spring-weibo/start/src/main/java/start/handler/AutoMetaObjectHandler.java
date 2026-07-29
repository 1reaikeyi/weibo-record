package start.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import start.security.SecurityContextParam;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis Plus自动填充处理器 - 自动填充创建时间、更新时间、创建人、更新人
 */
@Component
public class AutoMetaObjectHandler implements MetaObjectHandler {
    private Long getUserId(){
        Long userId = SecurityContextParam.getCurrentUserId();
        return userId != null ? userId : 0L;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        this.setFieldValByName("createTime", LocalDateTime.now(), metaObject);
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        this.setFieldValByName("createUser", getUserId(), metaObject);
        this.setFieldValByName("updateUser", getUserId(), metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        this.setFieldValByName("updateUser", getUserId(), metaObject);
    }
}