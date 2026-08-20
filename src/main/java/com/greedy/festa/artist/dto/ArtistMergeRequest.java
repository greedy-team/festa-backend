package com.greedy.festa.artist.dto;

import java.util.List;

public record ArtistMergeRequest(
        Long targetId, List<Long> sourceIds, Boolean keepAliases
) {

    public ArtistMergeRequest {
        if (keepAliases == null) {
            keepAliases = true;
        }
    }
}
