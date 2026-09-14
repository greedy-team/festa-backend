package com.greedy.festa.global.logging;

import com.greedy.festa.admin.controller.AdminAuthController;
import com.greedy.festa.admin.service.AdminAuthService;
import com.greedy.festa.global.config.ClockConfig;
import com.greedy.festa.global.config.SecurityConfig;
import com.greedy.festa.global.security.JwtAuthenticationEntryPoint;
import com.greedy.festa.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 필터가 체인에 실제로 걸렸는지, 그리고 요청 번호가 출력 형식에 실제로 나오는지는
 * 필터를 직접 호출해서는 증명되지 않는다. 진짜 요청을 흘려보내고 찍힌 줄을 읽는다.
 */
@WebMvcTest(AdminAuthController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class,
        ClockConfig.class, AccessLogFilterWebTest.테스트_컨트롤러.class})
@TestPropertySource(properties = {
        "app.jwt.admin-secret=0gC5vJgi622/FxmMt6/g8q1yuVJutf/BOwc2t27n7ao=",
        "app.jwt.admin-token-validity=PT1H"
})
@ExtendWith(OutputCaptureExtension.class)
@SuppressWarnings("NonAsciiCharacters")
public class AccessLogFilterWebTest {

    private static final String 요청_번호_모양 = "\\[[0-9a-f]{8}]";
    private static final Pattern 접속_기록_줄 = Pattern.compile("AccessLogFilter +: ");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AdminAuthService adminAuthService;

    // 로거 이름이 정확히 AccessLogFilter인 줄만 고른다. contains로 고르면 이 테스트 클래스가
    // 남긴 줄까지 딸려 온다 — 이름이 AccessLogFilter로 시작하기 때문이다.
    private List<String> 접속_기록(CapturedOutput 출력) {
        return 출력.getAll().lines()
                .filter(줄 -> 접속_기록_줄.matcher(줄).find())
                .toList();
    }

    @Test
    public void 접속_기록은_메서드_경로_상태_소요시간을_한_줄로_남긴다(CapturedOutput 출력) throws Exception {
        // when
        mockMvc.perform(get("/api/festivals")).andExpect(status().isOk());

        // then
        assertThat(접속_기록(출력)).singleElement().asString()
                .containsPattern("GET /api/festivals 200 \\d+ms");
    }

    @Test
    public void 접속_기록에_요청_번호가_함께_붙는다(CapturedOutput 출력) throws Exception {
        // when
        mockMvc.perform(get("/api/festivals")).andExpect(status().isOk());

        // then
        assertThat(접속_기록(출력)).singleElement().asString().containsPattern(요청_번호_모양);
    }

    @Test
    public void 요청_밖에서_남긴_로그에는_요청_번호가_붙지_않는다(CapturedOutput 출력) {
        // when
        LoggerFactory.getLogger(getClass()).info("요청 밖에서 남긴 줄");

        // then
        assertThat(출력.getAll().lines().filter(줄 -> 줄.contains("요청 밖에서 남긴 줄")).toList())
                .singleElement().asString().doesNotContainPattern(요청_번호_모양);
    }

    @Test
    public void 인증된_관리자_아이디가_함께_남는다(CapturedOutput 출력) throws Exception {
        // given
        String 토큰 = jwtTokenProvider.issue("jun");

        // when
        mockMvc.perform(get("/api/admin/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + 토큰))
                .andExpect(status().isOk());

        // then
        assertThat(접속_기록(출력)).singleElement().asString().contains("admin=jun");
    }

    @Test
    public void 인증이_막은_요청도_기록에_남는다(CapturedOutput 출력) throws Exception {
        // when
        mockMvc.perform(get("/api/admin/protected")).andExpect(status().isUnauthorized());

        // then
        assertThat(접속_기록(출력)).singleElement().asString()
                .contains("GET /api/admin/protected 401")
                .doesNotContain("admin=");
    }

    @Test
    public void 헬스_체크는_접속_기록에서_빠진다(CapturedOutput 출력) throws Exception {
        // when
        mockMvc.perform(get("/actuator/health"));

        // then
        assertThat(접속_기록(출력)).isEmpty();
    }

    @Test
    public void 접속자_IP는_기록에_남기지_않는다(CapturedOutput 출력) throws Exception {
        // when
        mockMvc.perform(get("/api/festivals").with(요청 -> {
            요청.setRemoteAddr("203.0.113.7");
            return 요청;
        })).andExpect(status().isOk());

        // then
        assertThat(접속_기록(출력)).singleElement().asString().doesNotContain("203.0.113.7");
    }

    @RestController
    static class 테스트_컨트롤러 {
        @GetMapping("/api/festivals")
        String festivals() {
            return "festivals";
        }

        @GetMapping("/api/admin/protected")
        String protectedResource() {
            return "ok";
        }
    }
}
