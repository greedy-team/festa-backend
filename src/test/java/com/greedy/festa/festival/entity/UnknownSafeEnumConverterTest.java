package com.greedy.festa.festival.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(OutputCaptureExtension.class)
class UnknownSafeEnumConverterTest {

    private final ExternalVisitorPolicyConverter converter = new ExternalVisitorPolicyConverter();

    @Test
    void 정상_enum_문자열은_WARN_없이_정상_enum으로_변환한다(CapturedOutput output) {
        ExternalVisitorPolicy result = converter.convertToEntityAttribute("ALLOWED");

        assertThat(result).isEqualTo(ExternalVisitorPolicy.ALLOWED);
        assertThat(output).doesNotContain("Unknown ExternalVisitorPolicy database value");
    }

    @Test
    void 미등록_레거시_문자열은_WARN을_남기고_UNKNOWN으로_변환한다(CapturedOutput output) {
        ExternalVisitorPolicy result = converter.convertToEntityAttribute("OUTSIDER_NEW");

        assertThat(result).isEqualTo(ExternalVisitorPolicy.UNKNOWN);
        assertThat(output).contains("Unknown ExternalVisitorPolicy database value: OUTSIDER_NEW");
    }

    @Test
    void literal_UNKNOWN도_WARN을_남기고_UNKNOWN으로_변환한다(CapturedOutput output) {
        ExternalVisitorPolicy result = converter.convertToEntityAttribute("UNKNOWN");

        assertThat(result).isEqualTo(ExternalVisitorPolicy.UNKNOWN);
        assertThat(output).contains("Unknown ExternalVisitorPolicy database value: UNKNOWN");
    }
}
