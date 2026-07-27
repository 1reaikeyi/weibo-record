package service.impl;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import mapper.ShopMapper;
import model.entity.Shop;
import org.springframework.stereotype.Service;
import service.ShopService;

import java.time.LocalDateTime;
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {
   
}
