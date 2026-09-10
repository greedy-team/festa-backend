package com.greedy.festa.search.service;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.global.util.LikePatternUtils;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.search.dto.SearchResponse;
import com.greedy.festa.support.PostgresTestSupport;
import com.greedy.festa.support.fixture.ArtistFixture;
import com.greedy.festa.support.fixture.FestivalFixture;
import com.greedy.festa.support.fixture.HostFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaConfig.class, SearchService.class,
        SearchServicePostgresIntegrationTest.FixedClockConfig.class})
class SearchSpacePostgresTest extends PostgresTestSupport {

    @Autowired EntityManager entityManager;
    @Autowired SearchService searchService;
    @Autowired ArtistRepository artistRepository;
    @Autowired FestivalRepository festivalRepository;

    static Stream<Arguments> searchFields() {
        return Stream.of("ARTIST_NAME", "ARTIST_ALIAS", "HOST_NAME", "HOST_SHORT_NAME", "FESTIVAL_NAME")
                .flatMap(field -> Stream.of("프로미스나인", "Literal%_\\Value", "Hy-phen.dot")
                        .map(text -> Arguments.of(field, text)));
    }

    @ParameterizedTest
    @MethodSource("searchFields")
    void spacesOnEitherSideMatchAllFieldsAndCounts(String field, String compact) {
        String spaced = compact.substring(0, 2) + "  " + compact.substring(2);
        List<Long> expected = List.of(create(field, compact, "one"), create(field, spaced, "two"));
        create(field, "unrelated", "other");
        if (field.equals("FESTIVAL_NAME")) {
            entityManager.persist(FestivalFixture.festival(compact + " hidden").build());
        }
        entityManager.flush();
        entityManager.clear();

        SearchResponse baseline = searchService.search(compact, "ALL");
        assertThat(ids(baseline, field)).containsExactlyElementsOf(expected);
        if (field.startsWith("HOST")) {
            assertThat(baseline.festivals()).hasSize(2);
        }
        for (String query : List.of(compact, spaced, "  " + spaced + "  ")) {
            SearchResponse all = searchService.search(query, "ALL");
            assertThat(all.query()).isEqualTo(query.trim());
            assertThat(all.artists()).isEqualTo(baseline.artists());
            assertThat(all.hosts()).isEqualTo(baseline.hosts());
            assertThat(all.festivals()).isEqualTo(baseline.festivals());
            for (String type : List.of("ARTIST", "HOST", "FESTIVAL")) {
                assertThat(searchService.search(query, type).counts()).isEqualTo(all.counts());
            }
            assertListQueries(field, LikePatternUtils.normalizeOptionalPattern(
                    query, 50, com.greedy.festa.search.exception.SearchErrorCode.SEARCH_INVALID_QUERY), expected);
        }
    }

    static Stream<Arguments> unchangedCharacters() {
        return Stream.of("ARTIST_NAME", "ARTIST_ALIAS", "HOST_NAME", "HOST_SHORT_NAME", "FESTIVAL_NAME")
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("unchangedCharacters")
    void onlyAsciiSpacesAreIgnored(String field) {
        Long expected = create(field, "scope", "plain");
        for (String separator : List.of("-", ".", "\t", "\n", "\u00a0", "\u3000", "%", "_", "\\")) {
            create(field, "sc" + separator + "ope", "variant" + (int) separator.charAt(0));
        }
        entityManager.flush();
        entityManager.clear();
        SearchResponse result = searchService.search("sc ope", "ALL");
        assertThat(ids(result, field)).containsExactly(expected);
        assertListQueries(field, LikePatternUtils.toSearchPattern("sc ope"), List.of(expected));
    }

    private Long create(String field, String value, String suffix) {
        if (field.startsWith("ARTIST")) {
            Artist artist = ArtistFixture.artist(field.equals("ARTIST_NAME") ? value : "artist-" + suffix).build();
            entityManager.persist(artist);
            if (field.equals("ARTIST_ALIAS")) {
                entityManager.persist(ArtistFixture.alias(artist, value).build());
                entityManager.persist(ArtistFixture.alias(artist, value + " extra").build());
            }
            return artist.getId();
        }
        Host host = HostFixture.host(field.equals("HOST_NAME") ? value : "host-" + suffix)
                .shortName(field.equals("HOST_SHORT_NAME") ? value : null).build();
        entityManager.persist(host);
        Festival festival = FestivalFixture.festival(field.equals("FESTIVAL_NAME") ? value : "festival-" + suffix)
                .host(host).build();
        festival.publish(Instant.parse("2026-08-01T00:00:00Z"));
        entityManager.persist(festival);
        return field.equals("FESTIVAL_NAME") ? festival.getId() : host.getId();
    }

    private List<Long> ids(SearchResponse response, String field) {
        if (field.startsWith("ARTIST")) return response.artists().stream().map(item -> item.artistId()).toList();
        if (field.startsWith("HOST")) return response.hosts().stream().map(item -> item.hostId()).toList();
        return response.festivals().stream().map(item -> item.festivalId()).toList();
    }

    private void assertListQueries(String field, String pattern, List<Long> expected) {
        // A full page of size one forces Spring Data to execute countQuery.
        PageRequest page = PageRequest.of(0, 1, Sort.by("id"));
        LocalDate today = LocalDate.of(2026, 8, 27);
        if (field.startsWith("ARTIST")) {
            assertPage(artistRepository.findAllWithAppearanceCount(null, null, pattern, page)
                    .map(row -> row.getArtist().getId()), expected);
            assertPage(artistRepository.findPublicByAppearances(null, pattern, today, PageRequest.of(0, 1))
                    .map(row -> row.getArtist().getId()), expected);
            Page<Long> byName = artistRepository.findPublicByName(null, pattern, today, PageRequest.of(0, 1))
                    .map(row -> row.getArtist().getId());
            assertThat(byName.getTotalElements()).isEqualTo(expected.size());
            assertThat(byName.getContent()).hasSize(1).allMatch(expected::contains);
        } else if (field.equals("FESTIVAL_NAME")) {
            assertPage(festivalRepository.findReviewRows(true, null, null, null, pattern, null, page)
                    .map(row -> row.getFestival().getId()), expected);
            assertPage(festivalRepository.findPublishedRows(null, null, null, null, null, today, pattern, page)
                    .map(Festival::getId), expected);
        }
    }

    private void assertPage(Page<Long> page, List<Long> expected) {
        assertThat(page.getTotalElements()).isEqualTo(expected.size());
        assertThat(page.getContent()).containsExactly(expected.getFirst());
    }
}
