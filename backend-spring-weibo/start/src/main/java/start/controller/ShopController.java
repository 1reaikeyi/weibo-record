package start.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import common.result.Result;
import common.result.ScrollResult;
import io.lettuce.core.api.async.RedisGeoAsyncCommands;
import model.dto.ShopDTO;
import model.entity.Shop;
import model.entity.ShopType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoLocation;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.web.bind.annotation.*;
import service.ShopService;
import service.ShopTypeService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/shop")
public class ShopController {
    @Autowired
    private ShopService shopService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ShopTypeService shopTypeService;
    private static final String SHOP_TYPE = "shopType:";

    @PostMapping
    public Result createShop(ShopDTO shopDTO){
        Shop shop = BeanUtil.toBean(shopDTO, Shop.class);
        ShopType shopType = BeanUtil.toBean(shopDTO, ShopType.class);
        shopService.save(shop);
        shopTypeService.save(shopType);
        Long typeId = shopType.getId();
        RedisGeoCommands.GeoLocation<String> location = new RedisGeoCommands.GeoLocation<String>(shop.getId().toString(),
                                                                new Point(shop.getX(),shop.getY()));
        stringRedisTemplate.opsForGeo().add(SHOP_TYPE+typeId,location);
        return Result.success(shopDTO);
    }
    @GetMapping("/of/type")
    public Result ofType(@RequestParam Long typeId,
                         @RequestParam(required = false) Long lastId,
                         @RequestParam(required = false) Long offset,
                         @RequestParam(required = false) Double x,
                         @RequestParam(required = false) Double y) {
        ScrollResult scrollResult = new ScrollResult();
        Long limit = offset == null ? 5L : offset;
        // 如果没有传经纬度，使用基于ID的滚动分页查询
        if( x == null && y == null ){
            List<Shop> shops = shopService.list(
                    new LambdaQueryWrapper<Shop>()
                            .eq(Shop::getTypeId, typeId)
                            .gt(Shop::getId, lastId != null ? lastId : 0)
                            .orderByAsc(Shop::getId)
                            .last("LIMIT "+limit)
            );
            if (CollectionUtil.isEmpty(shops)) {
                scrollResult.setList(null);
                scrollResult.setMinTime(0L);
                scrollResult.setOffset(limit);
                return Result.success(scrollResult);
            }
            scrollResult.setList(shops);
            scrollResult.setMinTime(shops.get(shops.size() - 1).getId());
            scrollResult.setOffset(limit);
            return Result.success(scrollResult);
        }
        // 如果传了经纬度，使用Redis GEO按距离排序查询附近店铺
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                SHOP_TYPE + typeId,
                GeoReference.fromCoordinate(x,y),
                new Distance(limit, Metrics.KILOMETERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(5).sortAscending()
        );
        if (CollectionUtil.isEmpty(results)){
            scrollResult.setList(null);
            scrollResult.setMinTime(0L);
            scrollResult.setOffset(limit);
            return Result.success(scrollResult);
        }
        List<Long> shopIds = results.getContent().stream()
                .map(item -> Long.valueOf(item.getContent().getName()))
                .toList();
        List<Shop> shopList= shopService.listByIds(shopIds);
        scrollResult.setList(shopList);
        scrollResult.setMinTime(shopIds.get(shopIds.size() - 1));
        scrollResult.setOffset(limit);
        return Result.success(scrollResult);
    }
}
