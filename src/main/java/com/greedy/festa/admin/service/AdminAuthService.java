package com.greedy.festa.admin.service;

import com.greedy.festa.admin.dto.AdminLoginRequest;
import com.greedy.festa.admin.dto.AdminLoginResponse;
import com.greedy.festa.admin.entity.AdminUser;
import com.greedy.festa.admin.exception.AdminErrorCode;
import com.greedy.festa.admin.repository.AdminUserRepository;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminUserRepository adminUserRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public AdminLoginResponse login(AdminLoginRequest request) {
        validateCredentials(request);

        AdminUser adminUser = adminUserRepository.findByUsername(request.username())
                .orElseThrow(() -> new FestaException(AdminErrorCode.ADMIN_INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(
                request.password(),
                adminUser.getPasswordHash()
        )) {
            throw new FestaException(AdminErrorCode.ADMIN_INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.issue(adminUser.getUsername());

        return AdminLoginResponse.of(accessToken, jwtTokenProvider.getValidity());
    }

    private void validateCredentials(AdminLoginRequest request) {
        if (request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            throw new FestaException(AdminErrorCode.ADMIN_INVALID_CREDENTIALS);
        }
    }
}
