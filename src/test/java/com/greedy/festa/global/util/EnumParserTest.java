package com.greedy.festa.global.util;

import com.greedy.festa.festival.dto.FestivalCoverageStatus;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.global.exception.FestaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SuppressWarnings("NonAsciiCharacters")
class EnumParserTest {

    private static final FestivalErrorCode ERROR = FestivalErrorCode.FESTIVAL_COVERAGE_INVALID_STATUS;

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void 값이_비어_있으면_기본값을_돌려준다(String value) {
        FestivalCoverageStatus 결과 = EnumParser.parse(
                FestivalCoverageStatus.class, value, FestivalCoverageStatus.NEEDS_CHECK, ERROR);

        assertThat(결과).isEqualTo(FestivalCoverageStatus.NEEDS_CHECK);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void 기본값을_받지_않는_오버로드는_빈_값에_null을_돌려준다(String value) {
        assertThat(EnumParser.parse(FestivalCoverageStatus.class, value, ERROR)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLISHED", "published", "PuBlIsHeD", "  published  ", "\tPUBLISHED\n"})
    void 대소문자와_앞뒤_공백을_흡수한다(String value) {
        FestivalCoverageStatus 결과 = EnumParser.parse(FestivalCoverageStatus.class, value, ERROR);

        assertThat(결과).isEqualTo(FestivalCoverageStatus.PUBLISHED);
    }

    @Test
    void 어휘_밖의_값은_넘겨받은_에러_코드로_거부한다() {
        FestaException 예외 = catchThrowableOfType(FestaException.class,
                () -> EnumParser.parse(FestivalCoverageStatus.class, "UNKNOWN", ERROR));

        assertThat(예외.getErrorCode()).isEqualTo(ERROR);
    }

    @Test
    void 기본값이_있어도_어휘_밖이면_거부한다() {
        // 기본값은 「값을 안 줬을 때」의 답이지 「틀린 값을 줬을 때」의 답이 아니다.
        // 여기서 기본값으로 흘려보내면 오타가 조용히 다른 필터로 바뀐다.
        FestaException 예외 = catchThrowableOfType(FestaException.class,
                () -> EnumParser.parse(FestivalCoverageStatus.class, "UNKNOWN",
                        FestivalCoverageStatus.NEEDS_CHECK, ERROR));

        assertThat(예외.getErrorCode()).isEqualTo(ERROR);
    }

    @Test
    void 기본_로케일이_터키어여도_소문자_i가_든_값을_변환한다() {
        // toUpperCase()에 Locale.ROOT가 빠지면 터키어 로케일에서 i -> İ(U+0130)가 되어
        // "published"가 "PUBLİSHED"로 올라가고 매칭이 깨진다. 서버 로케일에만 의존하는
        // 버그라 개발 기계에서는 절대 재현되지 않는다.
        Locale 원래 = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            assertThat(EnumParser.parse(FestivalCoverageStatus.class, "published", ERROR))
                    .isEqualTo(FestivalCoverageStatus.PUBLISHED);
        } finally {
            Locale.setDefault(원래);
        }
    }
}
