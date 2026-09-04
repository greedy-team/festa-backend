package com.greedy.festa.search.service;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.search.dto.SearchResponse;
import com.greedy.festa.support.PostgresTestSupport;
import com.greedy.festa.support.fixture.ArtistFixture;
import com.greedy.festa.support.fixture.FestivalFixture;
import com.greedy.festa.support.fixture.HostFixture;
import com.greedy.festa.support.fixture.LineupFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaConfig.class, SearchService.class,
        SearchServicePostgresIntegrationTest.FixedClockConfig.class})
class SearchServicePostgresIntegrationTest extends PostgresTestSupport {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 27);

    @Autowired
    private SearchService searchService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 이름_별칭_약칭_주최명은_대소문자_구분없이_부분_일치한다() {
        Host host = persist(HostFixture.host("Spring University")
                .shortName("봄대")
                .logoUrl("https://example.com/logo.png")
                .build());
        Artist artist = persist(ArtistFixture.artist("Season Band").build());
        persist(ArtistFixture.alias(artist, "SPRING CREW").build());
        Festival festival = festival(host, "Campus Day", TODAY.plusDays(10), true);
        flushAndClear();

        SearchResponse byEnglish = searchService.search("spring", "ALL");
        SearchResponse byShortName = searchService.search("봄", "ALL");
        SearchResponse byShortNameHostOnly = searchService.search("봄", "HOST");

        assertThat(byEnglish.artists()).extracting(item -> item.artistId())
                .containsExactly(artist.getId());
        assertThat(byEnglish.hosts()).extracting(item -> item.hostId())
                .containsExactly(host.getId());
        assertThat(byEnglish.festivals()).extracting(item -> item.festivalId())
                .containsExactly(festival.getId());
        assertThat(byShortName.hosts()).extracting(item -> item.hostId())
                .containsExactly(host.getId());
        assertThat(byShortName.artists()).isEmpty();
        assertThat(byShortName.festivals()).extracting(item -> item.festivalId())
                .containsExactly(festival.getId());
        assertThat(byShortName.counts().festival()).isEqualTo(1);
        assertThat(byShortNameHostOnly.festivals()).isEmpty();
        assertThat(byShortNameHostOnly.counts().festival()).isEqualTo(1);
    }

    @Test
    void 미공개_축제는_검색과_공개_집계에_노출하지_않는다() {
        Host host = persist(HostFixture.host("한국대학교")
                .shortName("한국대")
                .build());
        Artist artist = persist(ArtistFixture.artist("검색밴드").build());
        Festival publishedPast = festival(
                host, "공개 검색축제", TODAY.minusDays(20), true);
        Festival publishedRecent = festival(
                host, "최근 공개 검색축제", TODAY.minusDays(10), true);
        Festival unpublished = festival(
                host, "미공개 검색축제", TODAY.minusDays(5), false);
        lineup(artist, publishedPast);
        lineup(artist, publishedRecent);
        lineup(artist, unpublished);
        flushAndClear();

        SearchResponse response = searchService.search("검색", "ALL");

        assertThat(response.festivals()).extracting(item -> item.name())
                .containsExactly("공개 검색축제", "최근 공개 검색축제");
        assertThat(response.artists()).hasSize(1);
        assertThat(response.artists().getFirst().appearanceCount()).isEqualTo(2);
        assertThat(response.artists().getFirst().latestAppearanceDate())
                .isEqualTo(publishedRecent.getEndDate());
        assertThat(response.hosts()).isEmpty();

        SearchResponse hostResponse = searchService.search("한국대학교", "HOST");
        assertThat(hostResponse.hosts()).hasSize(1);
        assertThat(hostResponse.hosts().getFirst().festivalCount()).isEqualTo(2);
        assertThat(hostResponse.hosts().getFirst().latestFestivalYearMonth())
                .isEqualTo("2026-08");
    }

    @Test
    void LIKE_와일드카드와_escape_문자는_세_검색_대상에서_리터럴로_동작한다() {
        Host baseHost = persist(HostFixture.host("기준대학교")
                .shortName("기준대")
                .build());
        persist(HostFixture.host("Host%Name").build());
        persist(HostFixture.host("Host_Name").build());
        persist(HostFixture.host("Host\\Name").build());
        persist(HostFixture.host("HostXName").build());
        persist(ArtistFixture.artist("Artist%Name").build());
        persist(ArtistFixture.artist("Artist_Name").build());
        persist(ArtistFixture.artist("Artist\\Name").build());
        persist(ArtistFixture.artist("ArtistXName").build());
        festival(baseHost, "Festival%Name", TODAY.plusDays(1), true);
        festival(baseHost, "Festival_Name", TODAY.plusDays(2), true);
        festival(baseHost, "Festival\\Name", TODAY.plusDays(3), true);
        festival(baseHost, "FestivalXName", TODAY.plusDays(4), true);
        flushAndClear();

        assertLiteralSearch("%", "Artist%Name", "Host%Name", "Festival%Name");
        assertLiteralSearch("_", "Artist_Name", "Host_Name", "Festival_Name");
        assertLiteralSearch("\\", "Artist\\Name", "Host\\Name", "Festival\\Name");
    }

    @Test
    void 여러_alias와_lineup_join이_있어도_결과와_집계는_중복되지_않는다() {
        Host host = persist(HostFixture.host("봄 검색대학교")
                .shortName("봄검색대")
                .build());
        Artist artist = persist(ArtistFixture.artist("봄 검색밴드").build());
        persist(ArtistFixture.alias(artist, "봄 크루").build());
        persist(ArtistFixture.alias(artist, "봄 아티스트").build());
        Festival first = festival(host, "봄 검색축제", TODAY.minusDays(10), true);
        festival(host, "두 번째 공개축제", TODAY.plusDays(10), true);
        festival(host, "미공개축제", TODAY.plusDays(20), false);
        lineup(artist, first, 1, 1);
        lineup(artist, first, 2, 1);
        flushAndClear();

        SearchResponse response = searchService.search("봄", "ALL");

        assertThat(response.artists()).hasSize(1);
        assertThat(response.artists().getFirst().artistId()).isEqualTo(artist.getId());
        assertThat(response.artists().getFirst().appearanceCount()).isEqualTo(1);
        assertThat(response.hosts()).hasSize(1);
        assertThat(response.hosts().getFirst().festivalCount()).isEqualTo(2);
        assertThat(response.festivals()).hasSize(2)
                .extracting(item -> item.festivalId())
                .doesNotHaveDuplicates();
        assertThat(response.counts().artist()).isEqualTo(1);
        assertThat(response.counts().host()).isEqualTo(1);
        assertThat(response.counts().festival()).isEqualTo(2);
    }

    @Test
    void Artist_출연_집계는_KST_오늘보다_종료일이_앞선_축제만_포함한다() {
        Host host = persist(HostFixture.host("날짜대학교")
                .shortName("날짜대")
                .build());
        Artist artist = persist(ArtistFixture.artist("날짜경계밴드").build());
        Festival endedYesterday = festivalEndingOn(host, "어제 종료", TODAY.minusDays(1));
        Festival endsToday = festivalEndingOn(host, "오늘 종료", TODAY);
        lineup(artist, endedYesterday);
        lineup(artist, endsToday);
        flushAndClear();

        SearchResponse response = searchService.search("날짜경계", "ARTIST");

        assertThat(response.artists()).hasSize(1);
        assertThat(response.artists().getFirst().appearanceCount()).isEqualTo(1);
        assertThat(response.artists().getFirst().latestAppearanceDate())
                .isEqualTo(TODAY.minusDays(1));
    }

    private void assertLiteralSearch(
            String query, String artistName, String hostName, String festivalName
    ) {
        SearchResponse response = searchService.search(query, "ALL");

        assertThat(response.artists()).extracting(item -> item.name())
                .containsExactly(artistName);
        assertThat(response.hosts()).extracting(item -> item.name())
                .containsExactly(hostName);
        assertThat(response.festivals()).extracting(item -> item.name())
                .containsExactly(festivalName);
    }

    private Festival festivalEndingOn(Host host, String name, LocalDate endDate) {
        Festival festival = FestivalFixture.festival(name)
                .host(host)
                .startDate(endDate.minusDays(1))
                .endDate(endDate)
                .build();
        festival.publish(Instant.parse("2026-08-01T00:00:00Z"));
        return persist(festival);
    }

    private Festival festival(Host host, String name, LocalDate startDate, boolean published) {
        Festival festival = FestivalFixture.festival(name)
                .host(host)
                .startDate(startDate)
                .endDate(startDate.plusDays(2))
                .build();
        if (published) {
            festival.publish(Instant.parse("2026-08-01T00:00:00Z"));
        }
        return persist(festival);
    }

    private void lineup(Artist artist, Festival festival) {
        lineup(artist, festival, 1, 1);
    }

    private void lineup(Artist artist, Festival festival, int day, int displayOrder) {
        persist(LineupFixture.lineup(festival, artist)
                .day(day)
                .displayOrder(displayOrder)
                .build());
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock searchClock() {
            return Clock.fixed(Instant.parse("2026-08-26T15:30:00Z"), ZoneOffset.UTC);
        }
    }
}
