package com.greedy.festa.global.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigTest {

    private final ClockConfig clockConfig = new ClockConfig();

    @Test
    void clock은_utc를_유지하고_업무_날짜는_kst를_사용한다() {
        assertThat(clockConfig.clock().getZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(clockConfig.kstZoneId().getId()).isEqualTo("Asia/Seoul");

        Instant utc로는_전날인_시각 = Instant.parse("2026-05-20T15:30:00Z");
        LocalDate kst오늘 = LocalDate.now(
                Clock.fixed(utc로는_전날인_시각, ZoneOffset.UTC)
                        .withZone(clockConfig.kstZoneId()));

        assertThat(kst오늘).isEqualTo(LocalDate.of(2026, 5, 21));
    }
}
