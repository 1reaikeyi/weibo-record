package service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import mapper.UserFollowMapper;
import model.entity.UserFollow;
import org.springframework.stereotype.Service;
import service.UserFollowService;
@Service
public class UserFollowServiceImpl extends ServiceImpl<UserFollowMapper, UserFollow>  implements UserFollowService {
}
