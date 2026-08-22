package com.greedy.festa.global.security;

import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;


@SuppressWarnings("NonAsciiCharacters")
class JwtTokenProviderTest {

    private static final String 시크릿 = "0gC5vJgi622/FxmMt6/g8q1yuVJutf/BOwc2t27n7ao=";
    private static final String 다른_시크릿 = "YpoM+TvM4wnDJMZqcrM5XWp+Ipkc3eLQXwvgzFF02k8=";

    private static final Duration 수명 = Duration.ofMinutes(30);
    private static final Instant 발급시각 = Instant.parse("2026-08-18T00:00:00Z");

    private final JwtTokenProvider 발급자 = provider(시크릿, 발급시각);

    @Test
    void 발급한_토큰을_검증하면_담은_username이_나온다() {
        // given
        String token = 발급자.issue("admin");

        // when
        String username = 발급자.parseUsername(token);

        // then
        assertThat(username).isEqualTo("admin");
    }

    @Test
    void 수명_직전까지는_유효하다() {
        // given
        String token = 발급자.issue("admin");
        JwtTokenProvider 검증자 = provider(시크릿, 발급시각.plus(수명).minusSeconds(1));

        // when
        String username = 검증자.parseUsername(token);

        // then
        assertThat(username).isEqualTo("admin");
    }

    @Test
    void 수명이_지나면_TOKEN_EXPIRED로_구분된다() {
        // given — 만료 1초 후에 검증한다
        String token = 발급자.issue("admin");
        JwtTokenProvider 검증자 = provider(시크릿, 발급시각.plus(수명).plusSeconds(1));

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> 검증자.parseUsername(token)
        );

        // then — UNAUTHORIZED 로 뭉뚱그리면 필터가 401 두 종류를 못 가른다
        assertThat(thrown.getErrorCode()).isEqualTo(CommonErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void 다른_키로_서명한_토큰은_거부된다() {
        // given
        String 남의_토큰 = provider(다른_시크릿, 발급시각).issue("admin");

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> 발급자.parseUsername(남의_토큰)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    private static JwtTokenProvider provider(String secret, Instant now) {
        return new JwtTokenProvider(secret, 수명, Clock.fixed(now, ZoneOffset.UTC));
    }
}
