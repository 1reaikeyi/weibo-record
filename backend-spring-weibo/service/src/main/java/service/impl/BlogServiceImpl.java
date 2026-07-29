package service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import mapper.BlogMapper;
import model.entity.Blog;
import org.springframework.stereotype.Service;
import service.BlogService;
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {
}
