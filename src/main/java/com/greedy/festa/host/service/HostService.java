package com.greedy.festa.host.service;

import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.artist.repository.ArtistWithAppearanceCount;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.dto.HostDetailResponse;
import com.greedy.festa.host.dto.HostDetailResponse.FestivalHistory;
import com.greedy.festa.host.dto.HostDetailResponse.HistoryItem;
import com.greedy.festa.host.dto.HostDetailResponse.UpcomingFestival;
import com.greedy.festa.host.dto.HostDetailResponse.FrequentArtist;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.exception.HostErrorCode;
import com.greedy.festa.host.repository.HostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class HostService {

    private static final int HISTORY_SIZE = 2;
    private static final Limit FREQUENT_ARTIST_LIMIT = Limit.of(3);


    private final Clock clock;
    private final ZoneId kstZoneId;
    private final HostRepository hostRepository;
    private final FestivalRepository festivalRepository;
    private final ArtistRepository artistRepository;

    @Transactional(readOnly = true)
    public HostDetailResponse getHostDetail(Long hostId) {
        LocalDate today = LocalDate.now(clock.withZone(kstZoneId));

        Host target = hostRepository.findById(hostId)
                .orElseThrow(() -> new FestaException(HostErrorCode.HOST_NOT_FOUND));

        List<Festival> published = festivalRepository.findAllByHostIdAndPublishedAtIsNotNull(hostId);
        List<Integer> availableYears = published.stream()
                .map(festival -> festival.getStartDate().getYear())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
        List<UpcomingFestival> upcoming = published.stream()
                .filter(festival -> !festival.getEndDate().isBefore(today))
                .sorted(Comparator.comparing(Festival::getStartDate).thenComparing(Festival::getId))
                .map(festival -> UpcomingFestival.of(festival, today))
                .toList();

        List<Festival> ended = published.stream()
                .filter(festival -> festival.getEndDate().isBefore(today))
                .sorted(Comparator.comparing(Festival::getStartDate).reversed()
                        .thenComparing(Comparator.comparing(Festival::getId).reversed()))
                .toList();
        FestivalHistory history = FestivalHistory.of(
                ended.stream()
                        .limit(HISTORY_SIZE).map(HistoryItem::from)
                        .toList(),
                ended.size());

        List<ArtistWithAppearanceCount> rows = artistRepository
                .findFrequentArtistsByHostId(hostId, today, FREQUENT_ARTIST_LIMIT);

        List<FrequentArtist> frequentArtists = rows.stream()
                .map(FrequentArtist::from)
                .toList();

        return HostDetailResponse.of(target, availableYears, upcoming, history, frequentArtists);

    }
}
