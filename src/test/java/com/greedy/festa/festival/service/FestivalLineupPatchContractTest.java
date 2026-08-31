package com.greedy.festa.festival.service;

import com.greedy.festa.artist.dto.LineupUpdateRequest;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.Lineup;
import com.greedy.festa.artist.exception.LineupErrorCode;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.artist.repository.LineupRepository;
import com.greedy.festa.artist.service.LineupAdminService;
import com.greedy.festa.festival.dto.FestivalUpdateRequest;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.repository.HostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class FestivalLineupPatchContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private Festival festival;
    private FestivalAdminService festivals;
    private Host host;

    @BeforeEach
    void setUp() {
        FestivalRepository festivalRepository = mock(FestivalRepository.class);
        HostRepository hostRepository = mock(HostRepository.class);
        host = Host.builder().name("학교").region("서울").build();
        festival = Festival.builder().host(host).importKey("old").name("기존")
                .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 2)).build();
        given(festivalRepository.findDetailById(1L)).willReturn(Optional.of(festival));
        given(hostRepository.findById(2L)).willReturn(Optional.of(host));
        festivals = new FestivalAdminService(festivalRepository, hostRepository, Clock.systemUTC());
    }

    @Test
    void requiredFieldNullAndOmissionAreRejected() throws Exception {
        FestaException nullName = catchThrowableOfType(FestaException.class,
                () -> festivals.update(1L, festivalRequest("null", "\"2026-06-01\"")));
        FestaException omittedStart = catchThrowableOfType(FestaException.class,
                () -> festivals.update(1L, festivalRequest("\"새 축제\"", null)));
        assertThat(nullName.getErrorCode()).isEqualTo(FestivalErrorCode.FESTIVAL_INVALID_NAME);
        assertThat(omittedStart.getErrorCode()).isEqualTo(FestivalErrorCode.FESTIVAL_INVALID_DATE);
    }

    @Test
    void optionalStringNullAndOmissionAreRejected() throws Exception {
        FestivalUpdateRequest nullPoster = festivalRequest("\"새 축제\"", "\"2026-06-01\"");
        nullPoster.setPosterUrl(null);
        FestaException nullError = catchThrowableOfType(FestaException.class,
                () -> festivals.update(1L, nullPoster));
        FestivalUpdateRequest omittedPoster = festivalRequest("\"새 축제\"", "\"2026-06-01\"");
        java.lang.reflect.Field present = FestivalUpdateRequest.class.getDeclaredField("posterUrlPresent");
        present.setAccessible(true);
        present.setBoolean(omittedPoster, false);
        FestaException omittedError = catchThrowableOfType(FestaException.class,
                () -> festivals.update(1L, omittedPoster));
        assertThat(nullError.getErrorCode()).isEqualTo(FestivalErrorCode.FESTIVAL_INVALID_OPTIONAL_STRING);
        assertThat(omittedError.getErrorCode()).isEqualTo(FestivalErrorCode.FESTIVAL_INVALID_OPTIONAL_STRING);
    }

    @Test
    void normalValuesReplaceMultipleFieldsAndOmittedOptionalNonStringsDelete() throws Exception {
        FestivalUpdateRequest request = festivalRequest("\"새 축제\"", "\"2026-06-01\"");
        request.setPosterUrl("new-poster");
        festivals.update(1L, request);
        assertThat(festival.getName()).isEqualTo("새 축제");
        assertThat(festival.getPosterUrl()).isEqualTo("new-poster");
        assertThat(festival.getLatitude()).isNull();
        assertThat(festival.getExternalVisitor()).isNull();
    }

    @Test
    void lineupArtistValueNullAndOmissionFollowSecretGuestRule() throws Exception {
        LineupRepository repository = mock(LineupRepository.class);
        ArtistRepository artists = mock(ArtistRepository.class);
        Artist artist = Artist.builder().name("새 가수").build();
        Lineup lineup = Lineup.builder().festival(festival).artist(artist).day(1).displayOrder(1).build();
        given(repository.findDetailById(1L)).willReturn(Optional.of(lineup));
        given(artists.findById(9L)).willReturn(Optional.of(artist));
        LineupAdminService service = new LineupAdminService(repository, artists);

        service.update(1L, mapper.readValue("{\"artistId\":9,\"day\":2,\"displayOrder\":3}", LineupUpdateRequest.class));
        assertThat(lineup.getArtist()).isSameAs(artist);
        service.update(1L, mapper.readValue("{\"artistId\":null,\"day\":2,\"displayOrder\":3}", LineupUpdateRequest.class));
        assertThat(lineup.getArtist()).isNull();
        service.update(1L, mapper.readValue("{\"day\":2,\"displayOrder\":3}", LineupUpdateRequest.class));
        assertThat(lineup.getArtist()).isNull();
    }

    @Test
    void lineupRequiredFieldsRejectNullBlankAndOmission() throws Exception {
        LineupRepository repository = mock(LineupRepository.class);
        Lineup lineup = Lineup.builder().festival(festival).day(1).displayOrder(1).build();
        given(repository.findDetailById(1L)).willReturn(Optional.of(lineup));
        LineupAdminService service = new LineupAdminService(repository, mock(ArtistRepository.class));
        for (String json : new String[]{"{\"displayOrder\":1}", "{\"day\":null,\"displayOrder\":1}",
                "{\"day\":\"\",\"displayOrder\":1}"}) {
            FestaException error = catchThrowableOfType(FestaException.class,
                    () -> service.update(1L, mapper.readValue(json, LineupUpdateRequest.class)));
            assertThat(error.getErrorCode()).isEqualTo(LineupErrorCode.LINEUP_INVALID_DAY);
        }
    }

    private FestivalUpdateRequest festivalRequest(String name, String startDate) throws Exception {
        String start = startDate == null ? "" : ",\"startDate\":" + startDate;
        return mapper.readValue("""
                {"hostId":2,"importKey":"key","name":%s%s,"endDate":"2026-06-02",
                 "posterUrl":"","description":"","venueName":"","address":"",
                 "admissionNote":"","instagramUrl":""}
                """.formatted(name, start), FestivalUpdateRequest.class);
    }
}
