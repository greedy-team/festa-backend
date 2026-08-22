package com.greedy.festa.artist.dto;

import com.greedy.festa.artist.entity.Artist;

import java.util.List;

public record ArtistMergeResponse(
        Long targetId, String name, Integer mergedCount,
        Integer movedAppearances, Integer removedDuplicates,
        List<String> otherNames, Boolean needsReview
) {

    public static ArtistMergeResponse of(
            Artist target, int mergedCount, int movedAppearances,
            int removedDuplicates, List<String> otherNames
    ) {
        return new ArtistMergeResponse(
                target.getId(), target.getName(), mergedCount,
                movedAppearances, removedDuplicates, otherNames,
                target.isNeedsReview()
        );
    }
}
