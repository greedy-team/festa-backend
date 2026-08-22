package com.greedy.festa.global.config;

import com.greedy.festa.admin.controller.AdminAuthController;
import com.greedy.festa.admin.dto.AdminLoginResponse;
import com.greedy.festa.admin.service.AdminAuthService;
import com.greedy.festa.global.security.JwtAuthenticationEntryPoint;
import com.greedy.festa.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAuthController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class,
        ClockConfig.class, SecurityConfigTest.보호된_컨트롤러.class})
@TestPropertySource(properties = {
        "app.jwt.admin-secret=0gC5vJgi622/FxmMt6/g8q1yuVJutf/BOwc2t27n7ao=",
        "app.jwt.admin-token-validity=PT1H"
})
@SuppressWarnings("NonAsciiCharacters")
public class SecurityConfigTest {

    private static final String 시크릿 = "0gC5vJgi622/FxmMt6/g8q1yuVJutf/BOwc2t27n7ao=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AdminAuthService adminAuthService;

    @Test
    void 로그인_경로는_토큰_없이도_열려_있다() throws Exception {
        // given
        given(adminAuthService.login(any()))
                .willReturn(new AdminLoginResponse("발급된-토큰", 3600));

        // when & then
        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"pw"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("발급된-토큰"));
    }

    @Test
    void 토큰_없이_관리자_경로에_접근하면_401이다() throws Exception {
        mockMvc.perform(get("/admin/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void 유효한_토큰이면_통과한다() throws Exception {
        // given
        String 토큰 = jwtTokenProvider.issue("admin");

        // when & then
        mockMvc.perform(get("/admin/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + 토큰))
                .andExpect(status().isOk());
    }

    @Test
    void 만료된_토큰은_TOKEN_EXPIRED로_구분된다() throws Exception {
        // given
        String 만료된_토큰 = 과거에_발급한_토큰();

        // when & then
        mockMvc.perform(get("/admin/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + 만료된_토큰))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_EXPIRED"));
    }

    private String 과거에_발급한_토큰() {
        JwtTokenProvider 과거_발급자 = new JwtTokenProvider(
                시크릿,
                Duration.ofHours(1),
                Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));
        return 과거_발급자.issue("admin");
    }

    @RestController
    static class 보호된_컨트롤러 {
        @GetMapping("/admin/protected")
        String get() {
            return "ok";
        }
    }
}
