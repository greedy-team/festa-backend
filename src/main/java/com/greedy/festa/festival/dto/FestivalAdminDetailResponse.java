package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.entity.ExternalVisitorPolicy;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.entity.FestivalPublishBlocker;
import com.greedy.festa.festival.entity.TicketType;
import com.greedy.festa.festival.entity.VerificationMethod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record FestivalAdminDetailResponse(
        Long festivalId, String importKey, String name,
        Long hostId, String hostName,
        LocalDate startDate, LocalDate endDate,
        String posterUrl, String description,
        String venueName, String address, Double latitude, Double longitude,
        ExternalVisitorPolicy externalVisitor, VerificationMethod verification,
        TicketType ticketType, Instant ticketOpenAt,
        String admissionNote, String admissionRaw, String instagramUrl,
        Instant publishedAt, String discovery, String crawlFlag,
        String sourceUrl, Instant importedAt,
        long lineupCount, List<FestivalPublishBlocker> blockers,
        Instant createdAt, Instant updatedAt
) {
    public static FestivalAdminDetailResponse of(
            Festival festival, long lineupCount, List<FestivalPublishBlocker> blockers
    ) {
        return new FestivalAdminDetailResponse(
                festival.getId(), festival.getImportKey(), festival.getName(),
                festival.getHost() == null ? null : festival.getHost().getId(),
                festival.getHost() == null ? null : festival.getHost().getName(),
                festival.getStartDate(), festival.getEndDate(),
                festival.getPosterUrl(), festival.getDescription(),
                festival.getVenueName(), festival.getAddress(),
                festival.getLatitude(), festival.getLongitude(),
                festival.getExternalVisitor(), festival.getVerification(),
                festival.getTicketType(), festival.getTicketOpenAt(),
                festival.getAdmissionNote(), festival.getAdmissionRaw(), festival.getInstagramUrl(),
                festival.getPublishedAt(), festival.getDiscovery(), festival.getCrawlFlag(),
                festival.getSourceUrl(), festival.getImportedAt(), lineupCount, blockers,
                festival.getCreatedAt(), festival.getUpdatedAt()
        );
    }
}
