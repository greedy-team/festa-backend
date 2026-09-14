package com.greedy.festa.importer.model;

import com.greedy.festa.importer.entity.ImportBatchType;

import java.util.List;

public enum ImportSection {
    FESTIVALS(List.of(
            "import_key", "host_name", "name", "start_date", "end_date", "venue_name",
            "latitude", "longitude", "poster_url", "image_urls", "description", "hashtags",
            "external_visitor_policy", "verification_method", "ticket_type",
            "ticket_open_at", "admission_raw", "source_url", "discovery", "flag",
            "instagram_url"), ImportBatchType.FESTIVALS),
    LINEUPS(List.of(
            "import_key", "day", "order", "artist_raw", "artist_canonical", "revealed"),
            ImportBatchType.LINEUPS),
    ARTISTS(List.of("name", "other_names", "genre", "image_url", "needs_review"),
            ImportBatchType.ARTISTS);

    private final List<String> headers;
    private final ImportBatchType batchType;

    ImportSection(List<String> headers, ImportBatchType batchType) {
        this.headers = headers;
        this.batchType = batchType;
    }

    public List<String> headers() {
        return headers;
    }

    public ImportBatchType batchType() {
        return batchType;
    }

    public static ImportSection fromPath(String type) {
        return switch (type) {
            case "festivals" -> FESTIVALS;
            case "lineups" -> LINEUPS;
            case "artists" -> ARTISTS;
            default -> null;
        };
    }
}
