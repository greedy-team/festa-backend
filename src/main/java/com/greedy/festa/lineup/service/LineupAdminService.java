package com.greedy.festa.lineup.service;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.lineup.dto.LineupCreateRequest;
import com.greedy.festa.lineup.dto.LineupResponse;
import com.greedy.festa.lineup.dto.LineupUpdateRequest;
import com.greedy.festa.lineup.entity.Lineup;
import com.greedy.festa.lineup.exception.LineupErrorCode;
import com.greedy.festa.lineup.repository.LineupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LineupAdminService {

    private final FestivalRepository festivalRepository;
    private final ArtistRepository artistRepository;
    private final LineupRepository lineupRepository;

    @Transactional(readOnly = true)
    public LineupResponse findOne(Long festivalId, Long lineupId) {
        Lineup lineup = lineupRepository.findByIdAndFestivalId(lineupId, festivalId)
                .orElseThrow(() -> new FestaException(LineupErrorCode.LINEUP_NOT_FOUND));
        return LineupResponse.of(lineup);
    }

    @Transactional
    public LineupResponse create(Long festivalId, LineupCreateRequest request) {
        Festival festival = festivalRepository.findById(festivalId)
                .orElseThrow(() -> new FestaException(FestivalErrorCode.FESTIVAL_NOT_FOUND));

        validateDay(request.day(), festival);
        validateDisplayOrder(request.displayOrder());

        if (lineupRepository.existsByFestivalIdAndDayAndDisplayOrder(
                festivalId, request.day(), request.displayOrder())) {
            throw new FestaException(LineupErrorCode.LINEUP_DUPLICATE_SLOT);
        }

        Lineup lineup = lineupRepository.save(Lineup.builder()
                .festival(festival)
                .artist(findArtist(request.artistId()))
                .day(request.day())
                .displayOrder(request.displayOrder())
                .build()
        );

        return LineupResponse.of(lineup);
    }

    @Transactional
    public LineupResponse update(Long festivalId, Long lineupId, LineupUpdateRequest request) {
        Lineup lineup = lineupRepository.findByIdAndFestivalId(lineupId, festivalId)
                .orElseThrow(() -> new FestaException(LineupErrorCode.LINEUP_NOT_FOUND));

        validateDay(request.day(), lineup.getFestival());
        validateDisplayOrder(request.displayOrder());

        if (lineupRepository.existsByFestivalIdAndDayAndDisplayOrderAndIdNot(
                festivalId, request.day(), request.displayOrder(), lineupId)) {
            throw new FestaException(LineupErrorCode.LINEUP_DUPLICATE_SLOT);
        }

        lineup.update(findArtist(request.artistId()), request.day(), request.displayOrder());

        return LineupResponse.of(lineup);
    }

    @Transactional
    public void delete(Long festivalId, Long lineupId) {
        Lineup lineup = lineupRepository.findByIdAndFestivalId(lineupId, festivalId)
                .orElseThrow(() -> new FestaException(LineupErrorCode.LINEUP_NOT_FOUND));
        lineupRepository.delete(lineup);
    }

    private void validateDay(Integer day, Festival festival) {
        if (day == null || day < 1) {
            throw new FestaException(LineupErrorCode.LINEUP_INVALID_DAY);
        }
        if (!festival.withinPeriod(day)) {
            throw new FestaException(LineupErrorCode.LINEUP_DAY_OUT_OF_RANGE);
        }
    }

    private void validateDisplayOrder(Integer displayOrder) {
        if (displayOrder == null || displayOrder < 1) {
            throw new FestaException(LineupErrorCode.LINEUP_INVALID_DISPLAY_ORDER);
        }
    }

    private Artist findArtist(Long artistId) {
        if (artistId == null) {
            return null;
        }
        return artistRepository.findById(artistId)
                .orElseThrow(() -> new FestaException(ArtistErrorCode.ARTIST_NOT_FOUND));
    }
}
