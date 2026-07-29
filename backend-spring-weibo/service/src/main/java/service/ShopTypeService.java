package service;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import model.entity.ShopType;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;


public interface ShopTypeService  extends IService<ShopType> {
  
}
