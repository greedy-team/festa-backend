package com.greedy.festa.festival.service;

import com.greedy.festa.festival.dto.FestivalCardResponse;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.exception.FestaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FestivalService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int UPCOMING_MAX_LIMIT = 50;
    private static final int RECENT_MAX_LIMIT = 30;

    private final FestivalRepository festivalRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<FestivalCardResponse> getUpcomingFestivals(int limit) {
        validateLimit(limit, UPCOMING_MAX_LIMIT);

        LocalDate today = LocalDate.now(clock.withZone(SEOUL));

        List<Festival> festivals = festivalRepository.findPublishedNotEnded(today, Limit.of(limit));

        return festivals.stream()
                .map(FestivalCardResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FestivalCardResponse> getRecentPublished(int limit) {
        validateLimit(limit, RECENT_MAX_LIMIT);

        List<Festival> festivals = festivalRepository.findRecentlyPublished(Limit.of(limit));

        return festivals.stream()
                .map(FestivalCardResponse::from)
                .toList();
    }

    private void validateLimit(int limit, int maxLimit) {
        if (limit < 1 || limit > maxLimit) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_INVALID_LIMIT);
        }
    }
}
