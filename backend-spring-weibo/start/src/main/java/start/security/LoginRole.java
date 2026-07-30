package start.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
//test
@RestController
@EnableMethodSecurity(prePostEnabled = true)
public class LoginRole {
    @PreAuthorize("hasRole('ROLE_USER') or hasRole('ROLE_ADMIN')")
    @RequestMapping("/select")
    public String select() {
        return "hasRole('ROLE_USER') or hasRole('ROLE_ADMIN')+select";
    }
    @PreAuthorize("hasRole('ROLE_USER') or hasRole('ROLE_ADMIN')")
    @RequestMapping("/delete")
    public String delete() {
        return "hasRole('ROLE_ADMIN') + delete";
    }
}