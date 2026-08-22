package com.greedy.festa.importer.model;

import com.greedy.festa.importer.entity.ImportConflictPolicy;

import java.util.List;
import java.util.Map;

public record StoredPreviewRow(
        ImportSection section,
        int line,
        String importKey,
        ImportPreviewAction action,
        ImportConflictPolicy conflictPolicy,
        Map<String, Object> normalized,
        Map<String, String> payload,
        Long matchedHostId,
        Long matchedArtistId,
        Long matchedFestivalId,
        ArtistMatchStatus artistMatchStatus,
        List<PreviewProblem> errors,
        List<PreviewProblem> warnings,
        String skipReason,
        Boolean revealed,
        List<String> imageUrls,
        String ticketOpenAtRaw
) {
}
