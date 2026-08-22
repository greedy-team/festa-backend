package com.greedy.festa.importer.dto;

import com.greedy.festa.importer.model.ArtistMatchStatus;
import com.greedy.festa.importer.model.ImportPreviewAction;
import com.greedy.festa.importer.model.ImportSection;
import com.greedy.festa.importer.model.PreviewProblem;
import com.greedy.festa.importer.model.StoredPreviewRow;

import java.util.List;
import java.util.Map;

public record ImportPreviewRowResponse(
        ImportSection section,
        int line,
        String importKey,
        ImportPreviewAction action,
        Map<String, Object> values,
        Long matchedHostId,
        Long matchedArtistId,
        Long matchedFestivalId,
        ArtistMatchStatus artistMatchStatus,
        List<PreviewProblem> errors,
        List<PreviewProblem> warnings,
        String skipReason
) {
    public static ImportPreviewRowResponse from(StoredPreviewRow row) {
        return new ImportPreviewRowResponse(
                row.section(), row.line(), row.importKey(), row.action(), row.normalized(),
                row.matchedHostId(), row.matchedArtistId(), row.matchedFestivalId(),
                row.artistMatchStatus(), row.errors(), row.warnings(), row.skipReason());
    }
}
