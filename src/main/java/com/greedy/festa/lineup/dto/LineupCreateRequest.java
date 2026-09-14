package com.greedy.festa.lineup.dto;

public record LineupCreateRequest(
        Long artistId,
        Integer day,
        Integer displayOrder
) {
}
