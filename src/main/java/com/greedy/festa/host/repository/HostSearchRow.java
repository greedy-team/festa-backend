package com.greedy.festa.host.repository;

import com.greedy.festa.host.entity.Host;

import java.time.LocalDate;

public interface HostSearchRow {
    Host getHost();
    Long getFestivalCount();
    LocalDate getLatestFestivalDate();
}
