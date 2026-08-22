package com.greedy.festa.host.repository;

import java.time.LocalDate;

public interface HostCoverageRow {

    Long getHostId();
    String getHostName();
    String getInstagramUrl();
    Long getFestivalId();
    String getFestivalName();
    LocalDate getStartDate();
    LocalDate getEndDate();
    boolean getHasUnpublishedFestival();
    boolean getHasCurrentFestival();
}
