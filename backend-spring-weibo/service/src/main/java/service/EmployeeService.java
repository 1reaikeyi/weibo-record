package service;

import com.baomidou.mybatisplus.extension.service.IService;
import model.entity.Employee;

public interface EmployeeService extends IService<Employee> {
    Employee findByUsername(String username);
}
