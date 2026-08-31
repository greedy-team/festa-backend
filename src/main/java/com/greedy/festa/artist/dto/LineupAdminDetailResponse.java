package com.greedy.festa.artist.dto;

import com.greedy.festa.artist.entity.Lineup;

public record LineupAdminDetailResponse(
        Long lineupId,
        Long festivalId,
        String festivalName,
        Long artistId,
        String artistName,
        int day,
        int displayOrder
) {
    public static LineupAdminDetailResponse from(Lineup lineup) {
        return new LineupAdminDetailResponse(
                lineup.getId(), lineup.getFestival().getId(), lineup.getFestival().getName(),
                lineup.getArtist() == null ? null : lineup.getArtist().getId(),
                lineup.getArtist() == null ? null : lineup.getArtist().getName(),
                lineup.getDay(), lineup.getDisplayOrder()
        );
    }
}
