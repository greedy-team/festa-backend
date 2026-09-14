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
 * main 자동 배포(A1)가 타는 production 프로파일의 허용 목록을 지킨다.
 * 로컬 개발과 Vercel Preview는 개발 서버(dev-api)에 붙으므로 운영에는 공개 프론트만 둔다.
 * 프로파일 파일이 사라지면 공통 설정의 빈 값을 따라가 운영 프론트의 브라우저 호출이 전부 막힌다.
 */
@WebMvcTest(AdminAuthController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class,
        ClockConfig.class, SecurityConfigProductionCorsTest.보호된_컨트롤러.class})
@ActiveProfiles("production")
@TestPropertySource(properties = {
        "app.jwt.admin-secret=0gC5vJgi622/FxmMt6/g8q1yuVJutf/BOwc2t27n7ao=",
        "app.jwt.admin-token-validity=PT1H"
})
@SuppressWarnings("NonAsciiCharacters")
public class SecurityConfigProductionCorsTest {

    private static final String 운영_프론트_오리진 = "https://www.every-festa.com";
    private static final String 루트_도메인_오리진 = "https://every-festa.com";
    private static final String 로컬_프론트_오리진 = "http://localhost:3000";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAuthService adminAuthService;

    @Test
    void 운영_프론트_오리진의_프리플라이트가_통과한다() throws Exception {
        mockMvc.perform(options("/api/admin/protected")
                        .header(HttpHeaders.ORIGIN, 운영_프론트_오리진)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, 운영_프론트_오리진));
    }

    @Test
    void 루트_도메인_오리진도_통과한다() throws Exception {
        mockMvc.perform(options("/api/admin/protected")
                        .header(HttpHeaders.ORIGIN, 루트_도메인_오리진)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, 루트_도메인_오리진));
    }

    /**
     * development 목록을 그대로 복사해 오는 실수를 잡는다. 로컬은 dev-api에 붙는다.
     */
    @Test
    void 로컬_오리진은_운영에서_허용하지_않는다() throws Exception {
        MockHttpServletResponse 응답 = mockMvc.perform(options("/api/admin/protected")
                        .header(HttpHeaders.ORIGIN, 로컬_프론트_오리진)
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
