package service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import mapper.EmployeeMapper;
import model.entity.Employee;
import org.springframework.stereotype.Service;
import service.EmployeeService;
@Service
public class EmployeeServiceImpl extends ServiceImpl<EmployeeMapper, Employee> implements EmployeeService {
    @Override
    public Employee findByUsername(String username) {
        return this.lambdaQuery().eq(Employee::getUsername, username).one();
    }
}
