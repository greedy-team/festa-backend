package com.greedy.festa.admin.service;

import com.greedy.festa.admin.dto.AdminLoginRequest;
import com.greedy.festa.admin.dto.AdminLoginResponse;
import com.greedy.festa.admin.entity.AdminUser;
import com.greedy.festa.admin.exception.AdminErrorCode;
import com.greedy.festa.admin.repository.AdminUserRepository;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
public class AdminAuthServiceTest {

    private static final String 관리자_이름 = "admin";
    private static final String 잘못된_이름 = "not-admin";
    private static final String 원문_비밀번호 = "festa-admin-1234";
    private static final String 틀린_비밀번호 = "wrong-password";

    private static final String 저장된_해시 = "$2a$10$dummy.hash.never.verified.by.mock";

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AdminAuthService adminAuthService;

    @Test
    public void 아이디와_비밀번호가_맞으면_토큰을_발급한다() {
        // given
        AdminUser 관리자 = adminUser();
        given(adminUserRepository.findByUsername(관리자_이름))
                .willReturn(Optional.of(관리자));
        given(passwordEncoder.matches(원문_비밀번호, 저장된_해시)).willReturn(true);
        given(jwtTokenProvider.issue(관리자_이름)).willReturn("발급된-토큰");
        given(jwtTokenProvider.getValidity()).willReturn(Duration.ofHours(1));

        // when
        AdminLoginResponse 응답 = adminAuthService.login(new AdminLoginRequest(관리자_이름, 원문_비밀번호));

        // then
        assertSoftly(softly -> {
            softly.assertThat(응답.accessToken()).isEqualTo("발급된-토큰");
            softly.assertThat(응답.expiresIn()).isEqualTo(3600);
        });
    }

    @Test
    public void 없는_아이디로_로그인하면_로그인_실패한다() {
        // given
        given(adminUserRepository.findByUsername(잘못된_이름))
                .willReturn(Optional.empty());

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class,
                () -> adminAuthService.login(new AdminLoginRequest(잘못된_이름, 원문_비밀번호))
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(AdminErrorCode.ADMIN_INVALID_CREDENTIALS);
        verify(jwtTokenProvider, never()).issue(any());
    }

    @Test
    public void 비밀번호가_틀리면_로그인_실패한다() {
        //  given
        given(adminUserRepository.findByUsername(관리자_이름))
                .willReturn(Optional.of(adminUser()));
        given(passwordEncoder.matches(틀린_비밀번호, 저장된_해시)).willReturn(false);

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class,
                () -> adminAuthService.login(new AdminLoginRequest(관리자_이름, 틀린_비밀번호))
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(AdminErrorCode.ADMIN_INVALID_CREDENTIALS);
    }

    private AdminUser adminUser() {
        return AdminUser.builder()
                .username(관리자_이름)
                .passwordHash(저장된_해시)
                .build();
    }
}
