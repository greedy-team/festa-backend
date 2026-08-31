package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.entity.ExternalVisitorPolicy;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.entity.FestivalPublishBlocker;
import com.greedy.festa.festival.entity.TicketType;
import com.greedy.festa.festival.entity.VerificationMethod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record FestivalResponse(
        Long festivalId,
        Long hostId,
        String importKey,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String posterUrl,
        String description,
        String venueName,
        String address,
        Double latitude,
        Double longitude,
        ExternalVisitorPolicy externalVisitor,
        VerificationMethod verification,
        TicketType ticketType,
        Instant ticketOpenAt,
        String admissionNote,
        String instagramUrl,
        Instant publishedAt,
        List<FestivalPublishBlocker> blockers
) {

    public static FestivalResponse of(Festival festival, long lineupCount) {
        return new FestivalResponse(
                festival.getId(),
                hostId(festival),
                festival.getImportKey(),
                festival.getName(),
                festival.getStartDate(),
                festival.getEndDate(),
                festival.getPosterUrl(),
                festival.getDescription(),
                festival.getVenueName(),
                festival.getAddress(),
                festival.getLatitude(),
                festival.getLongitude(),
                festival.getExternalVisitor(),
                festival.getVerification(),
                festival.getTicketType(),
                festival.getTicketOpenAt(),
                festival.getAdmissionNote(),
                festival.getInstagramUrl(),
                festival.getPublishedAt(),
                FestivalPublishBlocker.evaluate(
                        festival.getHost() != null,
                        festival.getLatitude(),
                        festival.getLongitude(),
                        lineupCount
                )
        );
    }

    private static Long hostId(Festival festival) {
        if (festival.getHost() == null) {
            return null;
        }
        return festival.getHost().getId();
    }
}
