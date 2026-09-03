package com.greedy.festa.global.security;

import ch.qos.logback.classic.Level;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.support.LogCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@SuppressWarnings("NonAsciiCharacters")
public class JwtAuthenticationEntryPointTest {

    private static final String 요청_경로 = "/api/admin/festivals";

    private final JwtAuthenticationEntryPoint entryPoint =
            new JwtAuthenticationEntryPoint(new ObjectMapper());

    private LogCaptor 로그;

    @BeforeEach
    public void 로그를_받기_시작한다() {
        로그 = LogCaptor.forClass(JwtAuthenticationEntryPoint.class);
    }

    @AfterEach
    public void 로그를_떼어낸다() {
        로그.close();
    }

    private MockHttpServletRequest 요청() {
        return new MockHttpServletRequest("GET", 요청_경로);
    }

    @Test
    public void 만료된_토큰은_유형과_요청_경로가_남는다() throws IOException, ServletException {
        // given
        MockHttpServletRequest request = 요청();
        request.setAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE, CommonErrorCode.TOKEN_EXPIRED);

        // when
        entryPoint.commence(request, new MockHttpServletResponse(), null);

        // then
        assertThat(로그.messagesAt(Level.WARN))
                .anySatisfy(줄 -> assertSoftly(softly -> {
                    softly.assertThat(줄).contains(CommonErrorCode.TOKEN_EXPIRED.name());
                    softly.assertThat(줄).contains(요청_경로);
                }));
    }

    @Test
    public void 토큰_없는_요청은_UNAUTHORIZED로_남는다() throws IOException, ServletException {
        // when
        entryPoint.commence(요청(), new MockHttpServletResponse(), null);

        // then
        assertThat(로그.messagesAt(Level.WARN))
                .anySatisfy(줄 -> assertThat(줄).contains(CommonErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    public void 한_번의_인증_실패는_한_줄만_남긴다() throws IOException, ServletException {
        // when
        entryPoint.commence(요청(), new MockHttpServletResponse(), null);

        // then — 필터가 따로 남기지 않으므로 진입점의 한 줄이 전부여야 한다
        assertThat(로그.allMessages()).hasSize(1);
    }

    @Test
    public void 토큰_값은_로그에_남지_않는다() throws IOException, ServletException {
        // given
        MockHttpServletRequest request = 요청();
        request.addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.비밀토큰값.서명");

        // when
        entryPoint.commence(request, new MockHttpServletResponse(), null);

        // then
        assertThat(로그.allMessages())
                .allSatisfy(줄 -> assertSoftly(softly -> {
                    softly.assertThat(줄).doesNotContain("비밀토큰값");
                    softly.assertThat(줄).doesNotContain("Bearer");
                }));
    }
}
