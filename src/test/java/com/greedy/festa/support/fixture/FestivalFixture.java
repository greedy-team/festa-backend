package com.greedy.festa.support.fixture;

import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.host.entity.Host;

import java.time.LocalDate;

public final class FestivalFixture {

    public static final LocalDate START_DATE = LocalDate.of(2026, 5, 20);
    public static final LocalDate END_DATE = LocalDate.of(2026, 5, 22);
    public static final double LATITUDE = 37.5509;
    public static final double LONGITUDE = 127.0748;

    private FestivalFixture() {
    }

    public static Festival.FestivalBuilder festival(String name) {
        return Festival.builder()
                .name(name)
                .startDate(START_DATE)
                .endDate(END_DATE);
    }

    public static Festival.FestivalBuilder publishable(String name, Host host) {
        return festival(name)
                .host(host)
                .latitude(LATITUDE)
                .longitude(LONGITUDE);
    }
}
