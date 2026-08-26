package com.greedy.festa.admin.controller;

import com.greedy.festa.admin.dto.AdminLoginRequest;
import com.greedy.festa.admin.dto.AdminLoginResponse;
import com.greedy.festa.admin.exception.AdminErrorCode;
import com.greedy.festa.admin.service.AdminAuthService;
import com.greedy.festa.global.exception.ErrorResponse;
import com.greedy.festa.global.exception.FestaException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@WebMvcTest(controllers = AdminAuthController.class,
        excludeAutoConfiguration = OAuth2ClientWebSecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("NonAsciiCharacters")
class AdminAuthControllerTest {

    private static final String 관리자_이름 = "admin";
    private static final String 원문_비밀번호 = "festa-admin-1234";
    private static final String 틀린_비밀번호 = "wrong-password";
    private static final String 로그인_경로 = "/api/admin/auth/login";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AdminAuthService adminAuthService;

    @Test
    void 로그인에_성공하면_토큰과_만료시간을_반환한다() {
        // given
        given(adminAuthService.login(new AdminLoginRequest(관리자_이름, 원문_비밀번호)))
                .willReturn(new AdminLoginResponse("발급된-토큰", 3600));

        // when
        MvcTestResult 결과 = mvc.post().uri(로그인_경로)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}
                        """.formatted(관리자_이름, 원문_비밀번호))
                .exchange();

        // then
        assertThat(결과).hasStatusOk()
                .bodyJson().convertTo(AdminLoginResponse.class)
                .isEqualTo(new AdminLoginResponse("발급된-토큰", 3600));
    }

    @Test
    void 로그인에_실패하면_401과_에러_응답_4필드를_반환한다() {
        // given
        given(adminAuthService.login(any()))
                .willThrow(new FestaException(AdminErrorCode.ADMIN_INVALID_CREDENTIALS));

        // when
        MvcTestResult 결과 = mvc.post().uri(로그인_경로)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}
                        """.formatted(관리자_이름, 틀린_비밀번호))
                .exchange();

        // then
        assertThat(결과).hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().convertTo(ErrorResponse.class)
                .isEqualTo(new ErrorResponse(
                        "ADMIN_INVALID_CREDENTIALS",
                        "아이디 또는 비밀번호가 올바르지 않습니다",
                        401,
                        로그인_경로));
    }
}
