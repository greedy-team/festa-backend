package com.greedy.festa.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class JwtAuthenticationFilterTest {

    private static final String 시크릿 = "0gC5vJgi622/FxmMt6/g8q1yuVJutf/BOwc2t27n7ao=";
    private static final Instant 발급시각 = Instant.parse("2026-08-18T00:00:00Z");
    private static final String 관리자 = "festa-admin";
    private static final String 요청_경로 = "/api/admin/festivals";

    private final JwtTokenProvider 토큰발급자 = new JwtTokenProvider(
            시크릿, Duration.ofMinutes(30), Clock.fixed(발급시각, ZoneOffset.UTC));
    private final JwtAuthenticationFilter 필터 = new JwtAuthenticationFilter(토큰발급자);

    private final AtomicReference<String> 체인이_본_관리자 = new AtomicReference<>();

    @AfterEach
    void 남은_상태를_치운다() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest 요청() {
        return new MockHttpServletRequest("GET", 요청_경로);
    }

    private MockHttpServletRequest 요청(String 토큰) {
        MockHttpServletRequest request = 요청();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + 토큰);
        return request;
    }

    /** 로그가 실제로 찍히는 시점은 체인 안쪽이므로, 그때의 MDC를 붙잡아 둔다. */
    private FilterChain 체인() {
        return (request, response) ->
                체인이_본_관리자.set(MDC.get(JwtAuthenticationFilter.ADMIN_MDC_KEY));
    }

    @Test
    void 유효한_토큰이면_체인이_도는_동안_MDC에_관리자가_담긴다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = 요청(토큰발급자.issue(관리자));

        // when
        필터.doFilter(request, new MockHttpServletResponse(), 체인());

        // then
        assertThat(체인이_본_관리자.get()).isEqualTo(관리자);
    }

    @Test
    void 체인이_끝나면_MDC를_비운다() throws ServletException, IOException {
        // given: 톰캣이 스레드를 재사용하므로, 남겨두면 다음 요청의 로그에 이 관리자가 붙는다
        MockHttpServletRequest request = 요청(토큰발급자.issue(관리자));

        // when
        필터.doFilter(request, new MockHttpServletResponse(), 체인());

        // then
        assertThat(MDC.get(JwtAuthenticationFilter.ADMIN_MDC_KEY)).isNull();
    }

    @Test
    void 토큰이_없으면_MDC에_담기지_않는다() throws ServletException, IOException {
        // when
        필터.doFilter(요청(), new MockHttpServletResponse(), 체인());

        // then
        assertThat(체인이_본_관리자.get()).isNull();
    }

    @Test
    void 잘못된_토큰이면_MDC에_담기지_않고_에러_코드만_남는다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = 요청("서명이.맞지.않는토큰");

        // when
        필터.doFilter(request, new MockHttpServletResponse(), 체인());

        // then
        assertThat(체인이_본_관리자.get()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE)).isNotNull();
    }
}
