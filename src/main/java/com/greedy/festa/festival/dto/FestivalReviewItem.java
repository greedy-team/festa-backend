package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.entity.FestivalPublishBlocker;
import com.greedy.festa.host.entity.Host;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record FestivalReviewItem(
        Long festivalId,
        String importKey,
        String name,
        Long hostId,
        String hostName,
        LocalDate startDate,
        LocalDate endDate,
        boolean published,
        Instant publishedAt,
        String discovery,
        String sourceUrl,
        long lineupCount,
        Instant importedAt,
        List<FestivalPublishBlocker> blockers
) {

    public static FestivalReviewItem of(
            Festival festival, Host host, long lineupCount, List<FestivalPublishBlocker> blockers
    ) {
        Instant publishedAt = festival.getPublishedAt();
        return new FestivalReviewItem(
                festival.getId(),
                festival.getImportKey(),
                festival.getName(),
                host == null ? null : host.getId(),
                host == null ? null : host.getName(),
                festival.getStartDate(),
                festival.getEndDate(),
                publishedAt != null,
                publishedAt,
                festival.getDiscovery(),
                festival.getSourceUrl(),
                lineupCount,
                festival.getImportedAt(),
                blockers
        );
    }
}
