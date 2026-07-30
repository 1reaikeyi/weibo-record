package model.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
@Data
public class PassworEditEdDTO implements Serializable {
    /**
     * 旧密码
     */
    private String oldPassword;

    /**
     * 新密码
     */
    private String newPassword;
    /**
     * 确认密码
     */
    private String confirmPassword;
}
