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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
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
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"pw"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("발급된-토큰"));
    }

    @Test
    void 토큰_없이_관리자_경로에_접근하면_401이다() throws Exception {
        mockMvc.perform(get("/api/admin/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void 토큰_없이_임포트_이력에_접근하면_401이다() throws Exception {
        mockMvc.perform(get("/api/admin/imports"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void 공개_아티스트_목록과_상세는_토큰_없이_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/api/artists"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/artists/1"))
                .andExpect(status().isOk());
    }

    @Test
    void 공개_통합검색은_토큰_없이_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "봄"))
                .andExpect(status().isOk());
    }

    @Test
    void 유효한_토큰이면_통과한다() throws Exception {
        // given
        String 토큰 = jwtTokenProvider.issue("admin");

        // when & then
        mockMvc.perform(get("/api/admin/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + 토큰))
                .andExpect(status().isOk());
    }

    @Test
    void 만료된_토큰은_TOKEN_EXPIRED로_구분된다() throws Exception {
        // given
        String 만료된_토큰 = 과거에_발급한_토큰();

        // when & then
        mockMvc.perform(get("/api/admin/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + 만료된_토큰))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_EXPIRED"));
    }

    /**
     * 접두사 없는 옛 경로를 남겨두면 같은 자원에 경로가 둘이 된다. 그때 인증 매처는
     * `/api/admin/**` 한 벌뿐이라 옛 경로가 `anyRequest().permitAll()`로 새어 나간다 —
     * 401도 아니고 그냥 200이 되어 실패가 조용하다. 그래서 지키는 것은 「핸들러에 닿지
     * 않는다」 하나다.
     *
     * 상태 코드로 못박지 않는 이유가 있다. 지금 매핑 없는 경로는 404가 아니라 500이 나온다 —
     * `GlobalExceptionHandler`가 `@ExceptionHandler(Exception.class)`로 전부 잡아
     * `INTERNAL_SERVER_ERROR`를 내기 때문이며, 옛 관리자 경로든 아무 오타 경로든 똑같다.
     * 이 접두사 작업이 만든 것이 아니라 원래 그랬고, 고치는 것은 이 이슈 범위 밖이다.
     */
    @Test
    void 접두사_없는_옛_경로는_남아_있지_않다() throws Exception {
        MockHttpServletResponse 응답 = mockMvc.perform(get("/admin/protected")).andReturn().getResponse();

        assertThat(응답.getStatus()).isNotEqualTo(HttpStatus.OK.value());
        assertThat(응답.getContentAsString()).doesNotContain("ok");
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
        @GetMapping("/api/admin/protected")
        String get() {
            return "ok";
        }

        @GetMapping("/api/admin/imports")
        String imports() {
            return "imports";
        }

        @GetMapping("/api/artists")
        String artists() {
            return "artists";
        }

        @GetMapping("/api/artists/{id}")
        String artist() {
            return "artist";
        }

        @GetMapping("/api/search")
        String search() {
            return "search";
        }
    }
}
