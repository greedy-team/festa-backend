package com.greedy.festa.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
@SuppressWarnings("NonAsciiCharacters")
public class AccessLogFilterTest {

    private final AccessLogFilter filter = new AccessLogFilter();
    private final List<String> 요청_중_관찰한_번호 = new ArrayList<>();

    private final FilterChain 번호를_들여다보는_체인 = (request, response) ->
            요청_중_관찰한_번호.add(MDC.get(AccessLogFilter.REQUEST_ID));

    private final FilterChain 터지는_체인 = (request, response) -> {
        throw new IllegalStateException("boom");
    };

    @AfterEach
    public void MDC를_비운다() {
        MDC.clear();
    }

    private void 요청을_처리한다(FilterChain chain) throws ServletException, IOException {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/festivals"),
                new MockHttpServletResponse(), chain);
    }

    private List<String> 접속_기록(CapturedOutput 출력) {
        return 출력.getAll().lines()
                .filter(줄 -> 줄.contains("AccessLogFilter"))
                .toList();
    }

    @Test
    public void 요청이_도는_동안_요청_번호가_MDC에_들어있다() throws Exception {
        // when
        요청을_처리한다(번호를_들여다보는_체인);

        // then
        assertThat(요청_중_관찰한_번호).singleElement().asString().hasSize(8);
    }

    @Test
    public void 요청이_끝나면_요청_번호를_비운다() throws Exception {
        // when
        요청을_처리한다(번호를_들여다보는_체인);

        // then
        assertThat(MDC.get(AccessLogFilter.REQUEST_ID)).isNull();
    }

    @Test
    public void 체인이_예외를_던져도_요청_번호를_비운다() {
        // when
        assertThatThrownBy(() -> 요청을_처리한다(터지는_체인))
                .isInstanceOf(IllegalStateException.class);

        // then
        assertThat(MDC.get(AccessLogFilter.REQUEST_ID)).isNull();
    }

    @Test
    public void 연달아_처리한_두_요청은_서로_다른_번호를_받는다() throws Exception {
        // when
        요청을_처리한다(번호를_들여다보는_체인);
        요청을_처리한다(번호를_들여다보는_체인);

        // then
        assertThat(요청_중_관찰한_번호).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    public void 체인이_예외를_던지면_아직_안_나간_응답의_상태를_500으로_남긴다(CapturedOutput 출력) {
        // when
        assertThatThrownBy(() -> 요청을_처리한다(터지는_체인))
                .isInstanceOf(IllegalStateException.class);

        // then
        assertThat(접속_기록(출력)).singleElement().asString()
                .containsPattern("GET /api/festivals 500 [0-9]+ms");
    }

    @Test
    public void 체인이_예외를_던지면_예외_종류를_함께_남긴다(CapturedOutput 출력) {
        // when
        assertThatThrownBy(() -> 요청을_처리한다(터지는_체인))
                .isInstanceOf(IllegalStateException.class);

        // then
        assertThat(접속_기록(출력)).singleElement().asString()
                .contains("ex=IllegalStateException");
    }

    @Test
    public void 응답이_이미_나간_뒤_터지면_실제로_나간_상태를_남긴다(CapturedOutput 출력) {
        // given
        FilterChain 응답을_보낸_뒤_터지는_체인 = (request, response) -> {
            response.getWriter().write("이미 나갔다");
            response.flushBuffer();
            throw new IllegalStateException("boom");
        };

        // when
        assertThatThrownBy(() -> 요청을_처리한다(응답을_보낸_뒤_터지는_체인))
                .isInstanceOf(IllegalStateException.class);

        // then
        assertThat(접속_기록(출력)).singleElement().asString()
                .containsPattern("GET /api/festivals 200 [0-9]+ms");
    }
}
