package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.host.entity.Host;

import java.time.LocalDate;

public record FestivalCardResponse(
        Long festivalId, String name, HostSummaryResponse host,
        String posterUrl, LocalDate startDate, LocalDate endDate
) {

    public static FestivalCardResponse from(Festival festival) {
        Host host = festival.getHost();

        return new FestivalCardResponse(
                festival.getId(), festival.getName(), HostSummaryResponse.from(host),
                festival.getPosterUrl(), festival.getStartDate(), festival.getEndDate()
        );
    }

    public record HostSummaryResponse(Long id, String name, String logoUrl) {

        public static HostSummaryResponse from(Host host) {
            return new HostSummaryResponse(host.getId(), host.getName(), host.getLogoUrl());
        }
    }
}
