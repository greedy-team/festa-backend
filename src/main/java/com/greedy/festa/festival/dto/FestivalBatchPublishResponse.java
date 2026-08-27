package com.greedy.festa.festival.dto;

import java.util.List;

public record FestivalBatchPublishResponse(
        List<Long> publishedIds, List<FestivalPublishFailure> failed
) {
}
