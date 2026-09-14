package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.entity.ExternalVisitorPolicy;
import com.greedy.festa.festival.entity.TicketType;
import com.greedy.festa.festival.entity.VerificationMethod;

import java.time.Instant;
import java.time.LocalDate;

public record FestivalUpdateRequest(
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
        String instagramUrl
) {
}
