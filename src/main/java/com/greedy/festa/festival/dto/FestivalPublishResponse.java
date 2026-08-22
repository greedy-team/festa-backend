package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.entity.Festival;

import java.time.Instant;

public record FestivalPublishResponse(
        Long festivalId,
        String name,
        boolean published,
        Instant publishedAt
) {

    public static FestivalPublishResponse of(Festival festival) {
        Instant publishedAt = festival.getPublishedAt();
        return new FestivalPublishResponse(
                festival.getId(),
                festival.getName(),
                publishedAt != null,
                publishedAt
        );
    }
}
