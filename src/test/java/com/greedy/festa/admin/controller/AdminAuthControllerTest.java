package com.greedy.festa.admin.controller;

import com.greedy.festa.admin.dto.AdminLoginRequest;
import com.greedy.festa.admin.dto.AdminLoginResponse;
import com.greedy.festa.admin.service.AdminAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(controllers = AdminAuthController.class,
        excludeAutoConfiguration = OAuth2ClientWebSecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("NonAsciiCharacters")
class AdminAuthControllerTest {

    private static final String 관리자_이름 = "admin";
    private static final String 원문_비밀번호 = "festa-admin-1234";

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
        MvcTestResult 결과 = mvc.post().uri("/admin/auth/login")
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
}
