package com.greedy.festa.artist.dto;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistGenre;

import java.util.List;

public record ArtistMergeCandidateResponse(
        ArtistSummary source,
        List<ArtistCandidate> candidates
) {
    public record ArtistSummary(
            Long artistId,
            String name,
            List<String> otherNames,
            ArtistGenre genre,
            long appearanceCount
    ) {
        public ArtistSummary {
            if (otherNames == null) {
                otherNames = List.of();
            }
            else {
                otherNames = List.copyOf(otherNames);
            }
        }

        public static ArtistSummary of(Artist artist, List<String> otherNames, long appearanceCount) {
            return new ArtistSummary(
                    artist.getId(), artist.getName(), otherNames, artist.getGenre(), appearanceCount);
        }
    }

    public record ArtistCandidate(
            Long artistId,
            String name,
            List<String> otherNames,
            ArtistGenre genre,
            long appearanceCount,
            double similarity,
            List<ArtistMatchReason> reasons
    ) {

        public static ArtistCandidate of(Artist artist, List<String> otherNames, long appearanceCount,
                                         double similarity, List<ArtistMatchReason> reasons) {
            return new ArtistCandidate(
                    artist.getId(), artist.getName(), otherNames, artist.getGenre(), appearanceCount,
                    similarity, reasons);
        }
    }
}
