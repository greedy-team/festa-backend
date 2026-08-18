package com.greedy.festa.admin.config;

import com.greedy.festa.admin.entity.AdminUser;
import com.greedy.festa.admin.repository.AdminUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class AdminAccountSeeder implements ApplicationRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String initialUsername;
    private final String initialPassword;

    public AdminAccountSeeder(
            AdminUserRepository adminUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.initial-username:}") String initialUsername,
            @Value("${app.admin.initial-password:}") String initialPassword) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.initialUsername = initialUsername;
        this.initialPassword = initialPassword;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!StringUtils.hasText(initialUsername) || !StringUtils.hasText(initialPassword)) {
            log.info("초기 관리자 환경변수가 없어 시딩을 건너뜁니다");
            return;
        }

        if (adminUserRepository.existsByUsername(initialUsername)) {
            log.info("관리자 계정이 이미 있어 시딩을 건너뜁니다: {}", initialUsername);
            return;
        }

        adminUserRepository.save(AdminUser.builder()
                .username(initialUsername)
                .passwordHash(passwordEncoder.encode(initialPassword))
                .build());
        log.info("초기 관리자 계정을 생성했습니다: {}", initialUsername);
    }
}
