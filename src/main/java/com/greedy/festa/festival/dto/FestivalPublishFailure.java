package com.greedy.festa.festival.dto;

public record FestivalPublishFailure(
        Long festivalId, FestivalPublishFailureReason reason
) {
}
