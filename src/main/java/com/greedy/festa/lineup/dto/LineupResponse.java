package com.greedy.festa.lineup.dto;

import com.greedy.festa.lineup.entity.Lineup;

public record LineupResponse(
        Long lineupId,
        Long festivalId,
        Long artistId,
        int day,
        int displayOrder
) {

    public static LineupResponse of(Lineup lineup) {
        return new LineupResponse(
                lineup.getId(),
                lineup.getFestival().getId(),
                artistId(lineup),
                lineup.getDay(),
                lineup.getDisplayOrder()
        );
    }

    private static Long artistId(Lineup lineup) {
        if (lineup.getArtist() == null) {
            return null;
        }
        return lineup.getArtist().getId();
    }
}
