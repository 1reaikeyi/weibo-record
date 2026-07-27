package start.controller;

import common.result.Result;
import model.dto.ShopDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.ShopService;
import service.ShopTypeService;

@RestController
@RequestMapping("/shop")
public class ShopController {
    @Autowired
    private ShopService shopService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ShopTypeService shopTypeService;
    @PostMapping
    public Result createShop(ShopDTO shopDTO){
        return Result.success(shopDTO);
    }
    @GetMapping("/of/type")
    public Result ofType() {
        return Result.success();
    }
}
