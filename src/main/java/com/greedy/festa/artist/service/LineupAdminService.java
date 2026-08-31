package com.greedy.festa.artist.service;

import com.greedy.festa.artist.dto.LineupAdminDetailResponse;
import com.greedy.festa.artist.dto.LineupUpdateRequest;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.Lineup;
import com.greedy.festa.artist.exception.LineupErrorCode;
import com.greedy.festa.artist.repository.LineupRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import tools.jackson.databind.JsonNode;
import com.greedy.festa.global.exception.FestaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LineupAdminService {

    private final LineupRepository lineupRepository;
    private final ArtistRepository artistRepository;

    @Transactional(readOnly = true)
    public LineupAdminDetailResponse findOne(Long id) {
        Lineup lineup = lineupRepository.findDetailById(id)
                .orElseThrow(() -> new FestaException(LineupErrorCode.LINEUP_NOT_FOUND));
        return LineupAdminDetailResponse.from(lineup);
    }

    @Transactional
    public LineupAdminDetailResponse update(Long id, LineupUpdateRequest request) {
        Lineup lineup = lineupRepository.findDetailById(id)
                .orElseThrow(() -> new FestaException(LineupErrorCode.LINEUP_NOT_FOUND));
        int day = requiredPositive(request.day(), request.isDayPresent(), LineupErrorCode.LINEUP_INVALID_DAY);
        int displayOrder = requiredPositive(request.displayOrder(), request.isDisplayOrderPresent(),
                LineupErrorCode.LINEUP_INVALID_DISPLAY_ORDER);
        Long artistId = optionalId(request.artistId());
        Artist artist = artistId == null ? null : artistRepository.findById(artistId)
                .orElseThrow(() -> new FestaException(com.greedy.festa.artist.exception.ArtistErrorCode.ARTIST_NOT_FOUND));
        lineup.update(artist, day, displayOrder);
        return LineupAdminDetailResponse.from(lineup);
    }

    private int requiredPositive(JsonNode node, boolean present, LineupErrorCode code) {
        if (!present || node == null || node.isNull() || (node.isTextual() && node.asText().isBlank())) {
            throw new FestaException(code);
        }
        try {
            int value = Integer.parseInt(node.asText());
            if (value < 1) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new FestaException(code);
        }
    }

    private Long optionalId(JsonNode node) {
        if (node == null || node.isNull() || (node.isTextual() && node.asText().isBlank())) return null;
        try {
            return Long.valueOf(node.asText());
        } catch (NumberFormatException exception) {
            throw new FestaException(LineupErrorCode.LINEUP_INVALID_ARTIST_ID);
        }
    }
}
