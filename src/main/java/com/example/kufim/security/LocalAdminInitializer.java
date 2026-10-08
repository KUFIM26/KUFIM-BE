package com.example.kufim.security;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.admin.entity.AdminRole;
import com.example.kufim.admin.repository.AdminAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/** local 프로필에서만, 환경변수로 지정한 개발 계정 1개를 최초 생성합니다. */
@Configuration
@Profile("local")
public class LocalAdminInitializer {
    @Bean
    ApplicationRunner initializeLocalAdmin(AdminAccountRepository repository, PasswordEncoder encoder,
            @Value("${app.bootstrap-admin.login:}") String login,
            @Value("${app.bootstrap-admin.password:}") String password) {
        return args -> {
            if (login.isBlank() && password.isBlank()) return;
            if (login.isBlank() || password.isBlank()) {
                throw new IllegalStateException("개발 관리자 ID와 비밀번호를 모두 설정해 주세요.");
            }
            if (repository.findByLoginId(login).isEmpty()) {
                repository.save(AdminAccount.builder().loginId(login).passwordHash(encoder.encode(password))
                        .name("로컬 개발 관리자").role(AdminRole.SUPER_ADMIN).active(true).build());
            }
        };
    }
}
