package com.greedy.festa.admin.service;

import com.greedy.festa.admin.dto.AdminLoginRequest;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.BDDMockito.given;

/** 로그를 찍는 곳은 GlobalExceptionHandler이므로, 여기서는 그리로 흘러가는 logMessage를 본다. */
@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
public class AdminAuthServiceLoggingTest {

    private static final String 관리자_이름 = "admin";
    private static final String 원문_비밀번호 = "festa-admin-1234";
    private static final String 저장된_해시 = "$2a$10$dummy.hash.never.verified.by.mock";

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AdminAuthService adminAuthService;

    private AdminUser adminUser() {
        return AdminUser.builder()
                .username(관리자_이름)
                .passwordHash(저장된_해시)
                .build();
    }

    private FestaException 로그인_실패(AdminLoginRequest 요청) {
        return catchThrowableOfType(
                FestaException.class, () -> adminAuthService.login(요청));
    }

    @Test
    public void 없는_계정으로_로그인하면_시도한_아이디가_남는다() {
        // given
        given(adminUserRepository.findByUsername(관리자_이름)).willReturn(Optional.empty());

        // when
        FestaException 예외 = 로그인_실패(new AdminLoginRequest(관리자_이름, 원문_비밀번호));

        // then
        assertSoftly(softly -> {
            softly.assertThat(예외.getErrorCode()).isEqualTo(AdminErrorCode.ADMIN_INVALID_CREDENTIALS);
            softly.assertThat(예외.getLogMessage()).contains(관리자_이름);
            softly.assertThat(예외.getLogMessage()).doesNotContain(원문_비밀번호);
        });
    }

    @Test
    public void 비밀번호가_틀리면_비밀번호는_남지_않고_아이디만_남는다() {
        // given
        given(adminUserRepository.findByUsername(관리자_이름))
                .willReturn(Optional.of(adminUser()));
        given(passwordEncoder.matches(원문_비밀번호, 저장된_해시)).willReturn(false);

        // when
        FestaException 예외 = 로그인_실패(new AdminLoginRequest(관리자_이름, 원문_비밀번호));

        // then
        assertSoftly(softly -> {
            softly.assertThat(예외.getLogMessage()).contains(관리자_이름);
            softly.assertThat(예외.getLogMessage()).doesNotContain(원문_비밀번호);
            softly.assertThat(예외.getLogMessage()).doesNotContain(저장된_해시);
        });
    }

    @Test
    public void 없는_계정과_틀린_비밀번호는_응답은_같지만_로그로는_갈린다() {
        // given
        given(adminUserRepository.findByUsername(관리자_이름))
                .willReturn(Optional.empty());
        FestaException 계정_없음 = 로그인_실패(new AdminLoginRequest(관리자_이름, 원문_비밀번호));

        given(adminUserRepository.findByUsername(관리자_이름))
                .willReturn(Optional.of(adminUser()));
        given(passwordEncoder.matches(원문_비밀번호, 저장된_해시)).willReturn(false);
        FestaException 비밀번호_불일치 = 로그인_실패(new AdminLoginRequest(관리자_이름, 원문_비밀번호));

        // then — 에러 코드는 같아야 하고(계정 존재 여부를 숨긴다) 로그는 달라야 한다
        assertSoftly(softly -> {
            softly.assertThat(계정_없음.getErrorCode()).isEqualTo(비밀번호_불일치.getErrorCode());
            softly.assertThat(계정_없음.getMessage()).isEqualTo(비밀번호_불일치.getMessage());
            softly.assertThat(계정_없음.getLogMessage()).isNotEqualTo(비밀번호_불일치.getLogMessage());
        });
    }

    @Test
    public void 입력이_비면_값_대신_어느_칸이_비었는지가_남는다() {
        // when
        FestaException 예외 = 로그인_실패(new AdminLoginRequest("", 원문_비밀번호));

        // then
        assertSoftly(softly -> {
            softly.assertThat(예외.getLogMessage()).contains("입력 누락");
            softly.assertThat(예외.getLogMessage()).doesNotContain(원문_비밀번호);
        });
    }
}
