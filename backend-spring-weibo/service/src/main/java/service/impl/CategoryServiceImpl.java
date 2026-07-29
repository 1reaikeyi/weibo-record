package service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import model.entity.Category;
import mapper.CategoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import service.CategoryService;
import org.springframework.stereotype.Service;
import service.cache.RedisData;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 分类服务实现类 - 实现分类相关业务逻辑
 */
@Service
@Slf4j
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {
    private final static String KEYS = "category:";
    private final static String LOCK_KEY = "category:lock";
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Category readCache(Long id) {
//        1
//        Category category = lockCache(id);
//        2
        Category category = logicalCache(id);
        return category;
    }
    private Boolean cacheLocked(){
        return stringRedisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "locked", 5, TimeUnit.SECONDS);
    }
    private void cacheUnlock(){
        stringRedisTemplate.delete(LOCK_KEY);
    }

    private Category lockCache(Long id) {
        String key = KEYS + id;
//        1直接从缓存中获取数据
        String categoryJson = stringRedisTemplate.opsForValue().get(key);
//        2缓存不存在
        if (StrUtil.isBlank(categoryJson)) {
            Boolean success = cacheLocked();
            if (success) {
                try {
                    Category category = super.getById(id);
                    if (category == null) {
                        stringRedisTemplate.opsForValue().set(key, "",30, TimeUnit.SECONDS);
                        throw  new RuntimeException("id不存在");
                    }
                    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(category), 30, TimeUnit.MINUTES);
                    return category;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    if (success) {
                        cacheUnlock();
                    }
                }
            } else{
                throw new RuntimeException("服务异常，稍后重试");
            }
        }
//        3缓存存在
        Category category = JSONUtil.toBean(categoryJson, Category.class);
        return category;
    }
    private Category logicalCache(Long id) {
        String key = KEYS + id;
        //1 直接从缓存中获取数据
        String categoryJson = stringRedisTemplate.opsForValue().get(key);
        //2缓存不存在
        if (StrUtil.isBlank(categoryJson)) {
            Category category = super.getById(id);
            RedisData redisData = new RedisData();
            //没有id
            if (category == null) {
                redisData.setExpireTime(LocalDateTime.now().plusSeconds(30));
                redisData.setData(null);
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
                throw  new RuntimeException("id不存在");
            }
            //有id
            redisData.setData(category);
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(5));
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
            return category;
        }
        //3 缓存存在
        RedisData redisData = JSONUtil.toBean(categoryJson, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //过期
        if (expireTime.isBefore(LocalDateTime.now())) {
            log.info("Category缓存过期");
            Boolean success = cacheLocked();
            if (success) {
                try {
                    Category category = super.getById(id);
                    redisData.setExpireTime(LocalDateTime.now().plusSeconds(5));
                    redisData.setData(category);
                    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    if (success) {
                        cacheUnlock();
                    }
                }
            }else {
                throw new RuntimeException("服务器繁忙，请稍后重试");
            }
        }
        //正常
        log.info("Category缓存正常");
        Category category = BeanUtil.toBean(redisData.getData(), Category.class);
        return category;
    }

    @Override
    public Boolean updateCache(Category category) {
        boolean result = super.updateById(category);
        stringRedisTemplate.delete(KEYS+category.getId());
        return result;
    }

    @Override
    public Boolean deleteCache(Long id) {
        boolean result = super.removeById(id);
        stringRedisTemplate.delete(KEYS+id);
        return result;
    }
}