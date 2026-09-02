package com.greedy.festa.global.exception;

import ch.qos.logback.classic.Level;
import com.greedy.festa.support.LogCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@SuppressWarnings("NonAsciiCharacters")
public class GlobalExceptionHandlerLoggingTest {

    private static final String 도메인_맥락 = "festivalId=42, hostId=7";
    private static final String 요청_메서드 = "POST";
    private static final String 요청_경로 = "/api/admin/festivals/42";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private LogCaptor 로그;

    @BeforeEach
    public void 로그를_받기_시작한다() {
        로그 = LogCaptor.forClass(GlobalExceptionHandler.class);
    }

    @AfterEach
    public void 로그를_떼어낸다() {
        로그.close();
    }

    private MockHttpServletRequest 요청() {
        return new MockHttpServletRequest(요청_메서드, 요청_경로);
    }

    @Test
    public void 도메인_맥락은_로그에만_남고_응답_본문에는_실리지_않는다() {
        // given
        FestaException 예외 = new FestaException(CommonErrorCode.INVALID_PAGE, 도메인_맥락);

        // when
        ResponseEntity<ErrorResponse> 응답 = handler.handleFestaException(예외, 요청());

        // then
        assertSoftly(softly -> {
            softly.assertThat(로그.messagesAt(Level.WARN))
                    .anySatisfy(줄 -> assertThat(줄).contains(도메인_맥락));
            softly.assertThat(응답.getBody().message())
                    .isEqualTo(CommonErrorCode.INVALID_PAGE.getMessage());
            softly.assertThat(응답.getBody().message()).doesNotContain(도메인_맥락);
        });
    }

    @Test
    public void 도메인_맥락이_없어도_에러_코드와_요청_경로는_남는다() {
        // when
        handler.handleFestaException(new FestaException(CommonErrorCode.INVALID_PAGE), 요청());

        // then
        assertThat(로그.messagesAt(Level.WARN))
                .anySatisfy(줄 -> assertSoftly(softly -> {
                    softly.assertThat(줄).contains(CommonErrorCode.INVALID_PAGE.name());
                    softly.assertThat(줄).contains(요청_메서드);
                    softly.assertThat(줄).contains(요청_경로);
                }));
    }

    @Test
    public void 삼켜진_원인은_로그에_예외로_함께_실린다() {
        // given
        IOException 원인 = new IOException("stream closed");
        FestaException 예외 = new FestaException(CommonErrorCode.INVALID_PAGE, "CSV 읽기 실패", 원인);

        // when
        handler.handleFestaException(예외, 요청());

        // then
        assertThat(로그.thrown()).containsExactly(원인);
    }

    @Test
    public void 예상하지_못한_예외는_요청_경로와_함께_남긴다() {
        // when
        handler.handleException(new IllegalStateException("boom"), 요청());

        // then
        assertThat(로그.messagesAt(Level.ERROR))
                .anySatisfy(줄 -> assertSoftly(softly -> {
                    softly.assertThat(줄).contains(요청_메서드);
                    softly.assertThat(줄).contains(요청_경로);
                }));
    }

    @Test
    public void 업로드_크기_초과는_기록을_남긴다() {
        // when
        handler.handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(5_242_880L), 요청());

        // then
        assertThat(로그.messagesAt(Level.WARN))
                .anySatisfy(줄 -> assertSoftly(softly -> {
                    softly.assertThat(줄).contains(CommonErrorCode.PAYLOAD_TOO_LARGE.name());
                    softly.assertThat(줄).contains(요청_경로);
                }));
    }

    @Test
    public void 파일_누락은_빠진_파트_이름과_함께_남긴다() {
        // when
        handler.handleMissingServletRequestPartException(
                new MissingServletRequestPartException("file"), 요청());

        // then
        assertThat(로그.messagesAt(Level.WARN))
                .anySatisfy(줄 -> assertSoftly(softly -> {
                    softly.assertThat(줄).contains(CommonErrorCode.INVALID_REQUEST_BODY.name());
                    softly.assertThat(줄).contains("file");
                }));
    }

    @Test
    public void 응답_본문의_message는_언제나_에러_코드의_문구다() {
        // given
        FestaException 예외 = new FestaException(CommonErrorCode.UNAUTHORIZED, 도메인_맥락);

        // when
        ResponseEntity<ErrorResponse> 응답 = handler.handleFestaException(예외, 요청());

        // then
        assertSoftly(softly -> {
            softly.assertThat(예외.getMessage()).isEqualTo(CommonErrorCode.UNAUTHORIZED.getMessage());
            softly.assertThat(응답.getBody().message()).isEqualTo(CommonErrorCode.UNAUTHORIZED.getMessage());
            softly.assertThat(응답.getBody().instance()).isEqualTo(요청_경로);
        });
    }
}
