package service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import model.entity.Article;
import mapper.ArticleMapper;
import service.ArticleService;
import service.cache.RedisData;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 文章服务实现类 - 实现文章相关业务逻辑
 */
@Service
@Slf4j
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {
    private final static String KEYS = "article:";
    private final static String LOCK_KEY = "article:lock";
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Article readCache(Long id) {
        //1 物理锁
//        Article articleMysql = lockCache(id);
//        2逻辑锁
        Article articleMysql = logicCache(id);
        return articleMysql;
    }
    private Boolean cacheLock(){
        //对应 set key xxx nx, key为空写入xxx,否则返回false
        return stringRedisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "locked", 5, TimeUnit.SECONDS);
    }
    private void cacheUnlock(){
        stringRedisTemplate.delete(LOCK_KEY);
    }

    private Article lockCache(Long id){
        String key = KEYS + id;
        //1 直接从缓存中获取数据
        String value = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(value)){
            Article articleCache = JSONUtil.toBean(value, Article.class);
            return articleCache;
        }
        //2 缓存中没有数据，从数据库中查询
        Boolean success = cacheLock();
        if(success) {
            try {
                //3 从数据库中查询数据
                Article article = super.getById(id);
                //4 数据库中没有数据，返回空值
                if (article == null) {
                    //----缓存空值，过期时间60秒,解决缓存穿透问题
                    stringRedisTemplate.opsForValue().set(KEYS + id, "", 5, TimeUnit.SECONDS);
                    throw new RuntimeException("id不存在");
                }
                //5 缓存数据，过期时间30分钟
                stringRedisTemplate.opsForValue().set(KEYS + id, JSONUtil.toJsonStr(article), 30, TimeUnit.MINUTES);
                //6 返回数据
                return article;
            } catch (RuntimeException e) {
                throw new RuntimeException(e);
            } finally {
                //7 解锁
                if (success) {
                    cacheUnlock();
                    System.out.println("解锁" + LOCK_KEY);
                }
            }
        }else {
            throw new RuntimeException("服务器繁忙，请稍后重试");
        }
    }
    private Article logicCache(Long id){
        String key = KEYS + id;
        //1 直接从缓存中获取数据
        String value = stringRedisTemplate.opsForValue().get(key);
        //2.1 缓存中没有数据
        if(StrUtil.isBlank(value)){
            // 3查询数据库中的数据
            Article article = super.getById(id);
            RedisData redisData = new RedisData();
            if (article == null) {
                redisData.setData(null);
                redisData.setExpireTime(LocalDateTime.now().plusSeconds(30));
                stringRedisTemplate.opsForValue().set(KEYS + id, JSONUtil.toJsonStr(redisData));
                throw new RuntimeException("id不存在");
            }
            // 正常数据，redis实际不设置过期
            redisData.setData(article);
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(5));
            stringRedisTemplate.opsForValue().set(KEYS + id, JSONUtil.toJsonStr(redisData));
            return article;
        }
        //2.2 缓存中存在数据
        RedisData redisData = JSONUtil.toBean(value, RedisData.class);
        //3 检查缓存是否过期
        //过期，从数据库中查询数据
        if (redisData.getExpireTime().isBefore(LocalDateTime.now())) {
            log.info("Article缓存出现过期");
            Boolean success = cacheLock();
            if(success) {
                try {
                    Article article = super.getById(id);
                    redisData.setExpireTime(LocalDateTime.now().plusSeconds(5));
                    redisData.setData(article);
                    stringRedisTemplate.opsForValue().set(KEYS + id, JSONUtil.toJsonStr(redisData));
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
        //正常，返回缓存中的数据
        log.info("Article缓存正常");
        Article article = BeanUtil.toBean(redisData.getData(), Article.class);
        return article;
    }

    @Override
    public Boolean updateCache(Article article) {
        String key = KEYS + article.getId();
        //1 数据库中的数据更新后，删除缓存中的数据
        boolean result = super.updateById(article);
        //2 删除缓存中的数据
        stringRedisTemplate.delete(key);
        return result;
    }

    @Override
    public Boolean deleteCache(Long id) {
        String key = KEYS + id;
        //1 删除数据库中的数据
        boolean result = super.removeById(id);
        //2 删除缓存中的数据
        stringRedisTemplate.delete(key);
        return result;
    }

}