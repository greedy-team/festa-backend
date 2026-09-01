package com.greedy.festa.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
public class AccessLogFilterTest {

    private final AccessLogFilter filter = new AccessLogFilter();
    private final List<String> 요청_중_관찰한_번호 = new ArrayList<>();

    private final FilterChain 번호를_들여다보는_체인 = (request, response) ->
            요청_중_관찰한_번호.add(MDC.get(AccessLogFilter.REQUEST_ID));

    @AfterEach
    public void MDC를_비운다() {
        MDC.clear();
    }

    private void 요청을_처리한다(FilterChain chain) throws ServletException, IOException {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/festivals"),
                new MockHttpServletResponse(), chain);
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
        // given
        FilterChain 터지는_체인 = (request, response) -> {
            throw new IllegalStateException("boom");
        };

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
}
