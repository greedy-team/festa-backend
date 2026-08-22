package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.entity.Festival;

import java.time.Instant;

public record FestivalPublishResponse(
        Long festivalId,
        String name,
        Instant publishedAt
) {

    public static FestivalPublishResponse of(Festival festival) {
        return new FestivalPublishResponse(
                festival.getId(),
                festival.getName(),
                festival.getPublishedAt()
        );
    }
}
