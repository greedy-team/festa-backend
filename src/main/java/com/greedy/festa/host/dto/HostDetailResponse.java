package com.greedy.festa.host.dto;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.repository.ArtistWithAppearanceCount;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.host.entity.Host;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record HostDetailResponse(
        Long id, String name, String shortName, String region,
        String logoUrl, String bannerUrl, String homepageUrl,
        List<Integer> availableYears,
        List<UpcomingFestival> upcomingFestivals,
        FestivalHistory festivalHistory,
        List<FrequentArtist> frequentArtists
) {

    public static HostDetailResponse of(
            Host host, List<Integer> availableYears, List<UpcomingFestival> upcomingFestivals,
            FestivalHistory festivalHistory, List<FrequentArtist> frequentArtists
    ) {
        return new HostDetailResponse(
                host.getId(), host.getName(), host.getShortName(), host.getRegion(),
                host.getLogoUrl(), host.getBannerUrl(), host.getHomepageUrl(),
                availableYears, upcomingFestivals, festivalHistory, frequentArtists);
    }

    public record UpcomingFestival(
            Long festivalId, String name, String posterUrl,
            LocalDate startDate, LocalDate endDate, long dday
    ) {
        public static UpcomingFestival of(Festival festival, LocalDate today) {
            return new UpcomingFestival(
                    festival.getId(), festival.getName(), festival.getPosterUrl(),
                    festival.getStartDate(), festival.getEndDate(),
                    ChronoUnit.DAYS.between(today, festival.getStartDate()));
        }
    }

    public record FestivalHistory(List<HistoryItem> items, long total) {
        public FestivalHistory {
            items = List.copyOf(items);
        }

        public static FestivalHistory of(List<HistoryItem> items, long total) {
            return new FestivalHistory(items, total);
        }
    }

    public record HistoryItem(
            Long festivalId, String name, String posterUrl,
            LocalDate startDate, LocalDate endDate
    ) {
        public static HistoryItem from(Festival festival) {
            return new HistoryItem(
                    festival.getId(), festival.getName(), festival.getPosterUrl(),
                    festival.getStartDate(), festival.getEndDate());
        }
    }

    public record FrequentArtist(
            Long artistId, String name, String imageUrl, long appearanceCount
    ) {
        public static FrequentArtist from(ArtistWithAppearanceCount artistWithAppearanceCount) {
            Artist artist = artistWithAppearanceCount.getArtist();
            return new FrequentArtist(
                    artist.getId(), artist.getName(), artist.getImageUrl(),
                    artistWithAppearanceCount.getAppearanceCount());
        }
    }
}
