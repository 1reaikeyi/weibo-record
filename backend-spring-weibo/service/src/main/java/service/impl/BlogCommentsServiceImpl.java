package service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import mapper.BlogCommentsMapper;
import model.entity.BlogComments;
import org.springframework.stereotype.Service;
import service.BlogCommentsService;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements BlogCommentsService {
}
