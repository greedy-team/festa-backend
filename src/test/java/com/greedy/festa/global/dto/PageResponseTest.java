package com.greedy.festa.global.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class PageResponseTest {

    @Test
    @DisplayName("첫 페이지는 이전이 없고 마지막 페이지는 다음이 없다")
    void 첫_페이지와_마지막_페이지() {
        PageResponse<String> first = PageResponse.from(
                new PageImpl<>(List.of("연세대학교"), PageRequest.of(0, 10), 55));
        PageResponse<String> last = PageResponse.from(
                new PageImpl<>(List.of("연세대학교"), PageRequest.of(5, 10), 55));

        assertSoftly(softAssertions -> {
            softAssertions.assertThat(first.page()).isZero();
            softAssertions.assertThat(first.hasPrevious()).isFalse();
            softAssertions.assertThat(last.hasNext()).isFalse();
        });
    }
}
