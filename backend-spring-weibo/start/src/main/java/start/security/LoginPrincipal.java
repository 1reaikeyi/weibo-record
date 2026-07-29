package start.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

/**
 * 用户认证主体信息
 * 存入 SecurityContext 的 principal，方便取 id 和 username
 */
@Data
@AllArgsConstructor
public class LoginPrincipal {
    private Long id;
    private String username;
}
