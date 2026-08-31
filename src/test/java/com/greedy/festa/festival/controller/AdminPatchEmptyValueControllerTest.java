package com.greedy.festa.festival.controller;

import com.greedy.festa.artist.controller.LineupAdminController;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.Lineup;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.artist.repository.LineupRepository;
import com.greedy.festa.artist.service.LineupAdminService;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.festival.service.FestivalAdminService;
import com.greedy.festa.festival.service.FestivalCoverageService;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.repository.HostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminPatchEmptyValueControllerTest {

    @Test
    void festivalTypedEmptyStringsReachServiceAndDeleteValues() throws Exception {
        FestivalRepository festivals = mock(FestivalRepository.class);
        HostRepository hosts = mock(HostRepository.class);
        Host host = Host.builder().name("학교").region("서울").build();
        Festival festival = Festival.builder().host(host).importKey("old").name("기존")
                .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 2))
                .latitude(37.0).longitude(127.0).build();
        given(festivals.findDetailById(1L)).willReturn(Optional.of(festival));
        given(hosts.findById(2L)).willReturn(Optional.of(host));
        FestivalAdminService service = new FestivalAdminService(festivals, hosts, Clock.systemUTC());
        MockMvc mvc = standaloneSetup(new FestivalAdminController(service, mock(FestivalCoverageService.class))).build();

        mvc.perform(patch("/api/admin/festivals/1").contentType(MediaType.APPLICATION_JSON).content("""
                {"hostId":2,"importKey":"","name":"새 축제","startDate":"2026-06-01","endDate":"2026-06-02",
                 "posterUrl":"","description":"","venueName":"","address":"","latitude":"","longitude":"",
                 "externalVisitor":"","verification":"","ticketType":"","ticketOpenAt":"",
                 "admissionNote":"","instagramUrl":""}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.ticketOpenAt").doesNotExist());

        assertThat(festival.getLatitude()).isNull();
        assertThat(festival.getLongitude()).isNull();
        assertThat(festival.getExternalVisitor()).isNull();
        assertThat(festival.getTicketOpenAt()).isNull();
    }

    @Test
    void lineupBlankArtistIdBecomesSecretGuestThroughHttp() throws Exception {
        LineupRepository lineups = mock(LineupRepository.class);
        ArtistRepository artists = mock(ArtistRepository.class);
        Festival festival = Festival.builder().name("축제")
                .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 2)).build();
        Lineup lineup = Lineup.builder().festival(festival)
                .artist(Artist.builder().name("기존 가수").build()).day(1).displayOrder(1).build();
        given(lineups.findDetailById(1L)).willReturn(Optional.of(lineup));
        MockMvc mvc = standaloneSetup(new LineupAdminController(new LineupAdminService(lineups, artists))).build();

        mvc.perform(patch("/api/admin/lineups/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artistId\":\"\",\"day\":2,\"displayOrder\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artistId").doesNotExist())
                .andExpect(jsonPath("$.day").value(2));

        assertThat(lineup.getArtist()).isNull();
    }
}
