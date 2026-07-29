package service.impl;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import mapper.SignMapper;
import model.entity.Sign;
import org.springframework.stereotype.Service;
import service.SignService;

import java.time.LocalDateTime;
@Service
public class SignServiceImpl extends ServiceImpl<SignMapper, Sign> implements SignService {
    
}
