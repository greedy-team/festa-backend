package com.greedy.festa.lineup.dto;

public record LineupUpdateRequest(
        Long artistId,
        Integer day,
        Integer displayOrder
) {
}
