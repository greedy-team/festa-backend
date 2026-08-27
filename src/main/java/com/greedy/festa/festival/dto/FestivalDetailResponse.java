package com.greedy.festa.festival.dto;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.entity.Lineup;
import com.greedy.festa.festival.entity.ExternalVisitorPolicy;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.entity.TicketType;
import com.greedy.festa.festival.entity.VerificationMethod;
import com.greedy.festa.host.entity.Host;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;


public record FestivalDetailResponse(
        Long id, String name, HostResponse host,
        LocalDate startDate, LocalDate endDate,
        int dday,
        String posterUrl,
        List<LineupDayResponse> lineup,
        AdmissionResponse admission,
        LocationResponse location
) {

    public static FestivalDetailResponse of(
            Festival festival, List<Lineup> lineups, LocalDate today) {
        return new FestivalDetailResponse(
                festival.getId(),
                festival.getName(),
                HostResponse.from(festival.getHost()),
                festival.getStartDate(),
                festival.getEndDate(),
                (int) ChronoUnit.DAYS.between(today, festival.getStartDate()),
                festival.getPosterUrl(),
                lineupDays(festival.getStartDate(), lineups),
                AdmissionResponse.from(festival),
                LocationResponse.from(festival)
        );
    }

    public record HostResponse(
            Long id, String name, String logoUrl,
            String instagramUrl, String homepageUrl
    ) {

        public static HostResponse from(Host host) {
            return new HostResponse(
                    host.getId(), host.getName(), host.getLogoUrl(),
                    host.getInstagramUrl(), host.getHomepageUrl()
            );
        }
    }

    public record LineupDayResponse(
            int day, LocalDate date, List<LineupArtistResponse> artists
    ) {

        public static LineupDayResponse of(int day, LocalDate startDate, List<Lineup> lineups) {
            return new LineupDayResponse(
                    day,
                    startDate.plusDays(day - 1),
                    lineups.stream().map(LineupArtistResponse::from).toList()
            );
        }
    }

    public record LineupArtistResponse(
            Long id, String name, String imageUrl, ArtistGenre genre
    ) {

        public static LineupArtistResponse from(Lineup lineup) {
            Artist artist = lineup.getArtist();
            if (artist == null) {
                return new LineupArtistResponse(null, null, null, null);
            }
            return new LineupArtistResponse(
                    artist.getId(), artist.getName(), artist.getImageUrl(), artist.getGenre());
        }
    }

    public record AdmissionResponse(
            ExternalVisitorPolicy externalVisitor, VerificationMethod verification,
            TicketType ticketType, Instant ticketOpenAt, String note
    ) {

        public static AdmissionResponse from(Festival festival) {
            return new AdmissionResponse(
                    festival.getExternalVisitor(), festival.getVerification(),
                    festival.getTicketType(), festival.getTicketOpenAt(), festival.getAdmissionNote()
            );
        }
    }

    public record LocationResponse(
            String venueName, String address, Double latitude, Double longitude
    ) {

        public static LocationResponse from(Festival festival) {
            return new LocationResponse(
                    festival.getVenueName(), festival.getAddress(),
                    festival.getLatitude(), festival.getLongitude()
            );
        }
    }

    private static List<LineupDayResponse> lineupDays(LocalDate startDate, List<Lineup> lineups) {
        return lineups.stream()
                .collect(Collectors.groupingBy(
                        Lineup::getDay, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> LineupDayResponse.of(entry.getKey(), startDate, entry.getValue()))
                .toList();
    }
}
