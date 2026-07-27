package service.impl;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import mapper.ShopTypeMapper;
import model.entity.ShopType;
import org.springframework.stereotype.Service;
import service.ShopTypeService;

import java.time.LocalDateTime;
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements ShopTypeService {
    
}
