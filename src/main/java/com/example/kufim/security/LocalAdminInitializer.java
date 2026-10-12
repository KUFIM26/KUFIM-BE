package com.example.kufim.security;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.admin.entity.AdminRole;
import com.example.kufim.admin.repository.AdminAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 환경변수(KUFIM_ADMIN_LOGIN/PASSWORD)로 지정한 총괄 관리자 1개를 최초 생성합니다.
 * 운영(mysql) DB에도 첫 계정이 필요하므로 프로필과 무관하게 동작하며, 값이 없으면 아무것도 하지 않습니다.
 * 이미 같은 ID가 있으면 비밀번호를 덮어쓰지 않습니다. 운영에서는 첫 로그인 후 환경변수를 지웁니다.
 */
@Configuration
public class LocalAdminInitializer {
    @Bean
    ApplicationRunner initializeLocalAdmin(AdminAccountRepository repository, PasswordEncoder encoder,
            @Value("${app.bootstrap-admin.login:}") String login,
            @Value("${app.bootstrap-admin.password:}") String password) {
        return args -> {
            if (login.isBlank() && password.isBlank()) return;
            if (login.isBlank() || password.isBlank()) {
                throw new IllegalStateException("초기 관리자 ID와 비밀번호를 모두 설정해 주세요.");
            }
            if (repository.findByLoginId(login).isEmpty()) {
                repository.save(AdminAccount.builder().loginId(login).passwordHash(encoder.encode(password))
                        .name("총괄 관리자").role(AdminRole.SUPER_ADMIN).active(true).build());
            }
        };
    }
}
