package com.greedy.festa.global.config;

import com.greedy.festa.festival.entity.TicketType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JsonTest
@Import(JacksonCoercionConfig.class)
@DisplayName("전체 교체 계약 - 빈 문자열은 모든 타입에서 '비우기'로 도착한다")
class JacksonCoercionConfigTest {

    record Sample(LocalDate date, Instant instant, Long number, TicketType ticketType, String text) {
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 열거값의_빈_문자열은_null이_된다() {
        assertThat(objectMapper.readValue("{\"ticketType\":\"\"}", Sample.class).ticketType()).isNull();
    }

    @Test
    void 날짜_시각_숫자의_빈_문자열도_null이_된다() {
        assertThat(objectMapper.readValue("{\"date\":\"\"}", Sample.class).date()).isNull();
        assertThat(objectMapper.readValue("{\"instant\":\"\"}", Sample.class).instant()).isNull();
        assertThat(objectMapper.readValue("{\"number\":\"\"}", Sample.class).number()).isNull();
    }

    @Test
    void 문자열_필드의_빈_문자열은_보존된다() {
        assertThat(objectMapper.readValue("{\"text\":\"\"}", Sample.class).text()).isEmpty();
    }

    @Test
    void 형식이_틀린_값은_여전히_거부한다() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"date\":\"abc\"}", Sample.class))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> objectMapper.readValue("{\"ticketType\":\"NOPE\"}", Sample.class))
                .isInstanceOf(Exception.class);
    }
}
