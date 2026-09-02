package com.greedy.festa.festival.service;

import com.greedy.festa.festival.dto.FestivalDetailResponse;
import com.greedy.festa.festival.dto.FestivalListItemResponse;
import com.greedy.festa.festival.dto.FestivalRecentResponse;
import com.greedy.festa.festival.dto.FestivalSortType;
import com.greedy.festa.festival.dto.FestivalStatus;
import com.greedy.festa.festival.dto.FestivalUpcomingResponse;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.config.ClockConfig;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.global.util.LikePatternUtils;
import com.greedy.festa.lineup.entity.Lineup;
import com.greedy.festa.lineup.repository.LineupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FestivalService {

    private static final int UPCOMING_MAX_LIMIT = 50;
    private static final int RECENT_MAX_LIMIT = 30;
    private static final int LIST_MAX_PAGE_SIZE = 50;

    private final FestivalRepository festivalRepository;
    private final LineupRepository lineupRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<FestivalUpcomingResponse> getUpcomingFestivals(int limit) {
        validateLimit(limit, UPCOMING_MAX_LIMIT);

        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.KST));

        List<Festival> festivals = festivalRepository.findPublishedNotEnded(today, Limit.of(limit));

        return festivals.stream()
                .map(FestivalUpcomingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FestivalRecentResponse> getRecentPublished(int limit) {
        validateLimit(limit, RECENT_MAX_LIMIT);

        List<Festival> festivals = festivalRepository.findRecentlyPublished(Limit.of(limit));

        return festivals.stream()
                .map(FestivalRecentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<FestivalListItemResponse> getFestivals(
            Long hostId, Integer year, Long artistId, FestivalStatus status, String q,
            FestivalSortType sort, int page, int size
    ) {
        if (page < 0) {
            throw new FestaException(CommonErrorCode.INVALID_PAGE);
        }
        if (size < 1 || size > LIST_MAX_PAGE_SIZE) {
            throw new FestaException(CommonErrorCode.INVALID_PAGE_SIZE);
        }

        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.KST));
        LocalDate yearStart = null;
        LocalDate nextYearStart = null;
        if (year != null) {
            try {
                yearStart = LocalDate.of(year, 1, 1);
                nextYearStart = LocalDate.of(year + 1, 1, 1);
            } catch (DateTimeException e) {
                throw new FestaException(FestivalErrorCode.FESTIVAL_INVALID_FILTER);
            }
        }

        Page<Festival> festivals = festivalRepository.findPublishedRows(
                hostId, yearStart, nextYearStart, artistId,
                FestivalStatus.nameOrNull(status), today, normalizeQuery(q),
                PageRequest.of(page, size, sort.toSort())
        );

        return PageResponse.from(festivals.map(FestivalListItemResponse::from));
    }

    @Transactional(readOnly = true)
    public FestivalDetailResponse getFestivalDetail(Long id) {
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.KST));

        Festival target = festivalRepository.findPublishedDetailById(id)
                .orElseThrow(() -> new FestaException(FestivalErrorCode.FESTIVAL_NOT_FOUND));
        List<Lineup> lineups = lineupRepository.findDetailRowsByFestivalId(id);

        return FestivalDetailResponse.of(target, lineups, today);
    }

    private void validateLimit(int limit, int maxLimit) {
        if (limit < 1 || limit > maxLimit) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_INVALID_LIMIT);
        }
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String normalized = query.trim();
        if (normalized.length() > 50) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_INVALID_QUERY);
        }
        return LikePatternUtils.escape(normalized);
    }
}
