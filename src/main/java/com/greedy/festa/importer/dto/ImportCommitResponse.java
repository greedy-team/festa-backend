package com.greedy.festa.importer.dto;

import java.time.Instant;
import java.util.List;

public record ImportCommitResponse(
        Long importId,
        Instant committedAt,
        ImportCommitResult result,
        List<Long> createdFestivalIds
) {
}
