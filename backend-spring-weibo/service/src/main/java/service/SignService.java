package service;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import model.entity.Sign;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
@Service
public interface SignService  extends IService<Sign> {
   
}
