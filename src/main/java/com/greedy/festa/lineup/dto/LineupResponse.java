package com.greedy.festa.lineup.dto;

import com.greedy.festa.lineup.entity.Lineup;

public record LineupResponse(
        Long lineupId,
        Long festivalId,
        String festivalName,
        Long artistId,
        String artistName,
        int day,
        int displayOrder
) {

    public static LineupResponse of(Lineup lineup) {
        return new LineupResponse(
                lineup.getId(),
                lineup.getFestival().getId(),
                lineup.getFestival().getName(),
                artistId(lineup),
                artistName(lineup),
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

    private static String artistName(Lineup lineup) {
        if (lineup.getArtist() == null) {
            return null;
        }
        return lineup.getArtist().getName();
    }
}
