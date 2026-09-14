package com.greedy.festa.global.config;

import com.greedy.festa.admin.controller.AdminAuthController;
import com.greedy.festa.admin.service.AdminAuthService;
import com.greedy.festa.global.security.JwtAuthenticationEntryPoint;
import com.greedy.festa.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 허용 오리진을 테스트가 직접 못박지 않고 development 프로파일을 그대로 태운다.
 * develop 자동 배포가 타는 프로파일이 이것이므로, 지키는 것은 「CORS가 동작한다」가
 * 아니라 「배포에 실제로 실리는 허용 목록이 맞다」다. application-development.yml에서
 * 오리진을 지우거나 좁히면 여기서 깨진다.
 */
@WebMvcTest(AdminAuthController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class,
        ClockConfig.class, SecurityConfigCorsTest.보호된_컨트롤러.class})
@ActiveProfiles("development")
@TestPropertySource(properties = {
        "app.jwt.admin-secret=0gC5vJgi622/FxmMt6/g8q1yuVJutf/BOwc2t27n7ao=",
        "app.jwt.admin-token-validity=PT1H"
})
@SuppressWarnings("NonAsciiCharacters")
public class SecurityConfigCorsTest {

    private static final String 배포_프론트_오리진 = "https://every-festa.com";
    private static final String 로컬_프론트_오리진 = "http://localhost:3000";
    private static final String 허용되지_않은_오리진 = "https://every-festa.com.evil.example";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAuthService adminAuthService;

    @Test
    void 배포_프론트_오리진의_프리플라이트가_통과한다() throws Exception {
        mockMvc.perform(options("/api/admin/protected")
                        .header(HttpHeaders.ORIGIN, 배포_프론트_오리진)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, 배포_프론트_오리진));
    }

    /**
     * 배포 오리진을 더하면서 로컬을 덮어쓰는 실수를 잡는다. 목록이 쉼표로 이어 붙인
     * 한 줄이라 통째로 교체하기 쉽다.
     */
    @Test
    void 로컬_오리진도_그대로_남아_있다() throws Exception {
        mockMvc.perform(options("/api/admin/protected")
                        .header(HttpHeaders.ORIGIN, 로컬_프론트_오리진)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, 로컬_프론트_오리진));
    }

    /**
     * 허용 오리진을 접두어로 삼은 도메인을 쓴다. Vercel Preview를 받으려고
     * setAllowedOrigins를 setAllowedOriginPatterns로 바꾸면 완전 일치가 풀리는데,
     * 패턴을 넓게 잡으면 여기서 걸린다.
     */
    @Test
    void 허용되지_않은_오리진에는_허용_헤더가_붙지_않는다() throws Exception {
        MockHttpServletResponse 응답 = mockMvc.perform(options("/api/admin/protected")
                        .header(HttpHeaders.ORIGIN, 허용되지_않은_오리진)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andReturn()
                .getResponse();

        assertThat(응답.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @RestController
    static class 보호된_컨트롤러 {
        @GetMapping("/api/admin/protected")
        String get() {
            return "ok";
        }
    }
}
