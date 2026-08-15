package com.greedy.festa.importer.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportCommitPayload(
        @JsonProperty("import_key") String importKey,
        @JsonProperty("host_name") String hostName,
        @JsonProperty("name") String name,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        @JsonProperty("venue_name") String venueName,
        @JsonProperty("poster_url") String posterUrl,
        @JsonProperty("image_urls") List<String> imageUrls,
        @JsonProperty("description") String description,
        @JsonProperty("hashtags") List<String> hashtags,
        @JsonProperty("external_visitor_policy") String externalVisitorPolicy,
        @JsonProperty("verification_method") String verificationMethod,
        @JsonProperty("ticket_type") String ticketType,
        @JsonProperty("ticket_open_at") String ticketOpenAt,
        @JsonProperty("admission_raw") String admissionRaw,
        @JsonProperty("source_url") String sourceUrl,
        @JsonProperty("discovery") String discovery,
        @JsonProperty("flag") String flag,
        @JsonProperty("instagram_url") String instagramUrl,
        @JsonProperty("day") Integer day,
        @JsonProperty("order") Integer order,
        @JsonProperty("artist_raw") String artistRaw,
        @JsonProperty("artist_canonical") String artistCanonical,
        @JsonProperty("revealed") Boolean revealed,
        @JsonProperty("other_names") List<String> otherNames,
        @JsonProperty("genre") String genre,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("needs_review") Boolean needsReview
) {
}
