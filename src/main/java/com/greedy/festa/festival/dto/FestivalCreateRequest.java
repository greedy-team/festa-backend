package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.entity.ExternalVisitorPolicy;
import com.greedy.festa.festival.entity.TicketType;
import com.greedy.festa.festival.entity.VerificationMethod;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

public record FestivalCreateRequest(
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
        @Schema(implementation = String.class, allowableValues = {"ALLOWED", "CONDITIONAL", "DENIED"})
        ExternalVisitorPolicy externalVisitor,
        @Schema(implementation = String.class,
                allowableValues = {"NONE", "STUDENT_ID", "PRE_BOOKING", "INVITATION", "OTHER"})
        VerificationMethod verification,
        @Schema(implementation = String.class, allowableValues = {"FREE", "PAID"}) TicketType ticketType,
        Instant ticketOpenAt,
        String admissionNote,
        String instagramUrl
) {
}
