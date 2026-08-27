package com.greedy.festa.artist.repository;

import java.time.LocalDate;

public interface ArtistRecentFestivalRow {

    Long getArtistId();

    Long getFestivalId();

    String getFestivalName();

    String getHostShortName();

    LocalDate getEndDate();
}
