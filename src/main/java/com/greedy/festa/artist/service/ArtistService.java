package com.greedy.festa.artist.service;

import com.greedy.festa.artist.dto.ArtistAppearanceResponse;
import com.greedy.festa.artist.dto.ArtistDetailResponse;
import com.greedy.festa.artist.dto.ArtistListItemResponse;
import com.greedy.festa.artist.dto.ArtistPublicSortType;
import com.greedy.festa.artist.dto.ArtistSectionResponse;
import com.greedy.festa.artist.dto.ArtistUpcomingShowResponse;
import com.greedy.festa.artist.dto.RecentFestivalResponse;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistAlias;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.entity.Lineup;
import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRecentFestivalRow;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.artist.repository.ArtistWithAppearanceCount;
import com.greedy.festa.artist.repository.LineupRepository;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ArtistService {

    private static final int MAX_DETAIL_ITEMS = 5;

    private final ArtistRepository artistRepository;
    private final ArtistAliasRepository artistAliasRepository;
    private final LineupRepository lineupRepository;
    private final Clock clock;
    private final ZoneId kstZoneId;

    @Transactional(readOnly = true)
    public PageResponse<ArtistListItemResponse> findAll(
            int page, int size, String genreValue, String sortValue, String query
    ) {
        validatePage(page, size);
        ArtistGenre genre = ArtistGenre.from(genreValue);
        ArtistPublicSortType sort = ArtistPublicSortType.from(sortValue);
        String normalizedQuery = normalizeQuery(query);
        LocalDate today = today();

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<ArtistWithAppearanceCount> rows = sort == ArtistPublicSortType.NAME
                ? artistRepository.findPublicByName(genre, normalizedQuery, today, pageRequest)
                : artistRepository.findPublicByAppearances(genre, normalizedQuery, today, pageRequest);

        Map<Long, RecentFestivalResponse> recentFestivals = loadRecentFestivals(rows, today);
        return PageResponse.from(rows.map(row -> ArtistListItemResponse.from(
                row, recentFestivals.get(row.getArtist().getId()))));
    }

    @Transactional(readOnly = true)
    public ArtistDetailResponse findById(Long id) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new FestaException(ArtistErrorCode.ARTIST_NOT_FOUND));
        List<String> aliases = artistAliasRepository.findByArtistId(id).stream()
                .map(ArtistAlias::getName)
                .toList();

        LocalDate today = today();
        List<Lineup> lineups = lineupRepository.findPublishedByArtistId(id);

        List<ArtistUpcomingShowResponse> upcoming = lineups.stream()
                .map(lineup -> ArtistUpcomingShowResponse.from(lineup, today))
                .filter(show -> !show.performanceDate().isBefore(today)
                        && !show.performanceDate().isAfter(show.endDate()))
                .sorted(Comparator.comparing(ArtistUpcomingShowResponse::performanceDate)
                        .thenComparing(ArtistUpcomingShowResponse::festivalId)
                        .thenComparingInt(ArtistUpcomingShowResponse::day))
                .toList();

        Map<Long, ArtistAppearanceResponse> appearanceByFestival = new LinkedHashMap<>();
        lineups.stream()
                .filter(lineup -> lineup.getFestival().getEndDate().isBefore(today))
                .sorted(Comparator.comparing((Lineup lineup) -> lineup.getFestival().getStartDate()).reversed()
                        .thenComparing(lineup -> lineup.getFestival().getId(), Comparator.reverseOrder()))
                .forEach(lineup -> appearanceByFestival.putIfAbsent(
                        lineup.getFestival().getId(), ArtistAppearanceResponse.from(lineup.getFestival())));
        List<ArtistAppearanceResponse> appearances = List.copyOf(appearanceByFestival.values());

        return new ArtistDetailResponse(
                artist.getId(),
                artist.getName(),
                aliases,
                artist.getGenre(),
                null,
                artist.getInstagramUrl(),
                ArtistSectionResponse.from(upcoming, MAX_DETAIL_ITEMS),
                ArtistSectionResponse.from(appearances, MAX_DETAIL_ITEMS)
        );
    }

    private Map<Long, RecentFestivalResponse> loadRecentFestivals(
            Page<ArtistWithAppearanceCount> rows, LocalDate today
    ) {
        List<Long> artistIds = rows.stream()
                .map(row -> row.getArtist().getId())
                .toList();
        if (artistIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, RecentFestivalResponse> result = new LinkedHashMap<>();
        for (ArtistRecentFestivalRow row : artistRepository.findRecentFestivals(artistIds, today)) {
            result.putIfAbsent(row.getArtistId(), RecentFestivalResponse.from(row));
        }
        return result;
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new FestaException(CommonErrorCode.INVALID_PAGE);
        }
        if (size < 1 || size > 50) {
            throw new FestaException(CommonErrorCode.INVALID_PAGE_SIZE);
        }
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String normalized = query.trim();
        if (normalized.length() > 50) {
            throw new FestaException(ArtistErrorCode.ARTIST_INVALID_QUERY);
        }
        return escapeLikePattern(normalized);
    }

    private String escapeLikePattern(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(kstZoneId));
    }
}
