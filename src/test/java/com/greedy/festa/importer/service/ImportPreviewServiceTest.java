package com.greedy.festa.importer.service;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistAlias;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.repository.HostRepository;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.importer.dto.ImportPreviewResponse;
import com.greedy.festa.importer.entity.ImportBatch;
import com.greedy.festa.importer.entity.ImportConflictPolicy;
import com.greedy.festa.importer.exception.ImportErrorCode;
import com.greedy.festa.importer.model.ArtistMatchStatus;
import com.greedy.festa.importer.model.ImportPreviewAction;
import com.greedy.festa.importer.model.ImportSection;
import com.greedy.festa.importer.parser.ImportCsvParser;
import com.greedy.festa.importer.repository.ImportBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class ImportPreviewServiceTest {

    @Mock HostRepository hostRepository;
    @Mock ArtistRepository artistRepository;
    @Mock ArtistAliasRepository artistAliasRepository;
    @Mock FestivalRepository festivalRepository;
    @Mock ImportBatchRepository importBatchRepository;

    private PreviewJsonCodec codec;
    private ImportPreviewService service;

    @BeforeEach
    void setUp() {
        codec = new PreviewJsonCodec(JsonMapper.builder().findAndAddModules().build());
        service = new ImportPreviewService(
                new ImportCsvParser(), hostRepository, artistRepository, artistAliasRepository,
                festivalRepository, importBatchRepository, codec);
        lenient().when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> {
            ImportBatch batch = invocation.getArgument(0);
            ReflectionTestUtils.setField(batch, "id", 99L);
            return batch;
        });
    }

    @Test
    void bundle의_CREATE_Festival을_Lineup이_같은_importKey로_참조할_수_있다() {
        Host host = host(1L, "연세대학교", "연세대");
        given(hostRepository.findAllByNameIn(anyCollection())).willReturn(List.of(host));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of());

        ImportPreviewResponse response = service.previewBundle(
                festivalFile("연세대학교", "", "", ""),
                lineupFile("false", "", ""), null,
                ImportConflictPolicy.UPDATE, Instant.EPOCH);

        assertThat(response.rows()).hasSize(2);
        assertThat(response.rows()).allMatch(row -> row.action() == ImportPreviewAction.CREATE);
        assertThat(response.rows().get(1).importKey()).isEqualTo("연세대학교-2026");
        assertThat(response.rows().get(1).matchedFestivalId()).isNull();
        assertThat(response.rows().get(1).errors()).isEmpty();
    }

    @Test
    void 파일_수준_오류이면_ImportBatch를_저장하지_않는다() {
        MockMultipartFile invalidHeader = csv("file", "festivals.csv", "wrong\nvalue\n");

        assertThatThrownBy(() -> service.previewSingle(
                ImportSection.FESTIVALS, invalidHeader,
                ImportConflictPolicy.UPDATE, Instant.EPOCH))
                .isInstanceOf(FestaException.class)
                .extracting(exception -> ((FestaException) exception).getErrorCode())
                .isEqualTo(ImportErrorCode.IMPORT_INVALID_CSV_HEADER);
        verify(importBatchRepository, never()).save(any());
    }

    @Test
    void INVALID_row가_있어도_preview와_ImportBatch를_저장한다() {
        given(hostRepository.findAllByNameIn(anyCollection())).willReturn(List.of());
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of());

        ImportPreviewResponse response = service.previewSingle(
                ImportSection.FESTIVALS,
                festivalFile("없는대학교", "", "", ""),
                ImportConflictPolicy.UPDATE, Instant.EPOCH);

        assertThat(response.summary().invalid()).isOne();
        assertThat(response.blockers()).singleElement().satisfies(blocker -> {
            assertThat(blocker.code()).isEqualTo("HOST_NOT_FOUND");
            assertThat(blocker.count()).isOne();
            assertThat(blocker.values()).containsExactly("없는대학교");
        });
        verify(importBatchRepository).save(any(ImportBatch.class));
    }

    @Test
    void 같은_importKey_day_order의_Lineup은_모두_INVALID다() {
        Festival existing = Festival.builder()
                .host(host(1L, "연세대학교", "연세대"))
                .importKey("연세대학교-2026").name("대동제")
                .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 2))
                .build();
        ReflectionTestUtils.setField(existing, "id", 3L);
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of(existing));
        String header = String.join(",", ImportSection.LINEUPS.headers());
        String row = "연세대학교-2026,1,1,,,false";

        ImportPreviewResponse response = service.previewSingle(
                ImportSection.LINEUPS,
                csv("file", "lineup.csv", header + "\n" + row + "\n" + row + "\n"),
                ImportConflictPolicy.UPDATE, Instant.EPOCH);

        assertThat(response.summary().invalid()).isEqualTo(2);
        assertThat(response.blockers()).anySatisfy(blocker -> {
            assertThat(blocker.code()).isEqualTo("DUPLICATE_LINEUP_POSITION");
            assertThat(blocker.count()).isEqualTo(2);
            assertThat(blocker.values()).containsExactly("연세대학교-2026");
        });
    }

    @Test
    void bundle은_실제_도메인_데이터를_저장하지_않고_preview_batch만_저장한다() {
        Host host = host(1L, "연세대학교", "연세대");
        given(hostRepository.findAllByNameIn(anyCollection())).willReturn(List.of(host));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of());
        Instant uploadedAt = Instant.parse("2026-08-19T00:00:00Z");

        ImportPreviewResponse response = service.previewBundle(
                festivalFile("연세대학교", "", "https://cdn.example.com/1.jpg|https://cdn.example.com/2.jpg",
                        "2026-05-07T14:00:00+09:00"),
                lineupFile("false", "", ""), null, null, uploadedAt);

        assertSoftly(softly -> {
            softly.assertThat(response.importId()).isEqualTo(99L);
            softly.assertThat(response.onConflict()).isEqualTo(ImportConflictPolicy.UPDATE);
            softly.assertThat(response.expiresAt()).isEqualTo(uploadedAt.plusSeconds(1800));
            softly.assertThat(response.summary().toCreate()).isEqualTo(2);
            softly.assertThat(response.summary().invalid()).isZero();
            softly.assertThat(response.rows().getFirst().values().get("posterUrl"))
                    .isEqualTo("https://cdn.example.com/1.jpg");
            softly.assertThat(response.rows().get(1).values().get("revealed")).isEqualTo(false);
        });
        verify(importBatchRepository).save(any(ImportBatch.class));
        verify(artistRepository, never()).save(any());
        verify(festivalRepository, never()).save(any());
    }

    @Test
    void 중복_import_key는_각_Festival_row를_INVALID로_표시한다() {
        Host host = host(1L, "연세대학교", "연세대");
        given(hostRepository.findAllByNameIn(anyCollection())).willReturn(List.of(host));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of());
        String header = String.join(",", ImportSection.FESTIVALS.headers());
        String row = festivalRow("연세대학교", "", "", "2026-05-07T14:00:00");
        MockMultipartFile file = csv("file", "festivals.csv", header + "\n" + row + "\n" + row + "\n");

        ImportPreviewResponse response = service.previewSingle(
                ImportSection.FESTIVALS, file, ImportConflictPolicy.UPDATE, Instant.EPOCH);

        assertThat(response.summary().invalid()).isEqualTo(2);
        assertThat(response.rows()).allMatch(item -> item.action() == ImportPreviewAction.INVALID);
        assertThat(response.blockers()).anyMatch(item -> item.code().equals("DUPLICATE_IMPORT_KEY")
                && item.count() == 2);
    }

    @Test
    void Host는_name_exact_match만_사용하고_shortName은_매칭하지_않는다() {
        given(hostRepository.findAllByNameIn(anyCollection())).willReturn(List.of());
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of());

        ImportPreviewResponse response = service.previewSingle(
                ImportSection.FESTIVALS,
                festivalFile("연세대", "https://example.com/poster.jpg", "", ""),
                ImportConflictPolicy.UPDATE, Instant.EPOCH);

        assertThat(response.rows().getFirst().action()).isEqualTo(ImportPreviewAction.INVALID);
        assertThat(response.blockers()).extracting("code").contains("HOST_NOT_FOUND");
    }

    @Test
    void Artist는_name_alias_NEW_UNRESOLVED를_구분하고_alias를_합집합한다() {
        Artist direct = artist(10L, "10CM");
        Artist aliasTarget = artist(20L, "십센치 정식명");
        Artist conflict = artist(30L, "충돌 이름");
        ArtistAlias fallback = alias(aliasTarget, "십센치");
        ArtistAlias conflictingAlias = alias(conflict, "10CM");
        given(artistRepository.findAllByNameIn(anyCollection())).willReturn(List.of(direct));
        given(artistAliasRepository.findAllWithArtistByNameIn(anyCollection()))
                .willReturn(List.of(fallback, conflictingAlias));
        given(artistAliasRepository.findAllByArtistIdIn(anyCollection())).willReturn(List.of());

        ImportPreviewResponse response = service.previewSingle(
                ImportSection.ARTISTS, artistsFile(), ImportConflictPolicy.UPDATE, Instant.EPOCH);

        assertSoftly(softly -> {
            softly.assertThat(response.rows().get(0).artistMatchStatus())
                    .isEqualTo(ArtistMatchStatus.UNRESOLVED);
            softly.assertThat(response.rows().get(0).action()).isEqualTo(ImportPreviewAction.INVALID);
            softly.assertThat(response.rows().get(1).artistMatchStatus())
                    .isEqualTo(ArtistMatchStatus.MATCHED);
            softly.assertThat(response.rows().get(1).matchedArtistId()).isEqualTo(20L);
            softly.assertThat(response.rows().get(2).artistMatchStatus()).isEqualTo(ArtistMatchStatus.NEW);
            softly.assertThat(response.rows().get(2).matchedArtistId()).isNull();
        });
    }

    @Test
    void UPDATE의_빈_poster_url은_기존값을_유지하고_offset없는_시간은_서울로_해석한다() {
        Host host = host(1L, "연세대학교", "연세대");
        Festival existing = Festival.builder()
                .host(host).importKey("연세대학교-2026").name("기존 축제")
                .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 2))
                .posterUrl("https://example.com/old.jpg").build();
        ReflectionTestUtils.setField(existing, "id", 5L);
        given(hostRepository.findAllByNameIn(anyCollection())).willReturn(List.of(host));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of(existing));

        ImportPreviewResponse response = service.previewSingle(
                ImportSection.FESTIVALS,
                festivalFile("연세대학교", "", "", "2026-05-07T14:00:00"),
                ImportConflictPolicy.UPDATE, Instant.EPOCH);

        assertThat(response.rows().getFirst().action()).isEqualTo(ImportPreviewAction.UPDATE);
        assertThat(response.rows().getFirst().values())
                .containsEntry("posterUrl", "https://example.com/old.jpg")
                .containsEntry("ticketOpenAt", Instant.parse("2026-05-07T05:00:00Z"));
    }

    @Test
    void 기존_importKey는_onConflict에_따라_UPDATE와_SKIP으로_판정한다() {
        Host host = host(1L, "연세대학교", "연세대");
        Festival existing = Festival.builder()
                .host(host).importKey("연세대학교-2026").name("기존 축제")
                .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 2))
                .build();
        ReflectionTestUtils.setField(existing, "id", 5L);
        given(hostRepository.findAllByNameIn(anyCollection())).willReturn(List.of(host));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of(existing));

        ImportPreviewResponse update = service.previewSingle(
                ImportSection.FESTIVALS, festivalFile("연세대학교", "", "", ""),
                ImportConflictPolicy.UPDATE, Instant.EPOCH);
        ImportPreviewResponse skip = service.previewSingle(
                ImportSection.FESTIVALS, festivalFile("연세대학교", "", "", ""),
                ImportConflictPolicy.SKIP, Instant.EPOCH);

        assertThat(update.rows().getFirst().action()).isEqualTo(ImportPreviewAction.UPDATE);
        assertThat(skip.rows().getFirst().action()).isEqualTo(ImportPreviewAction.SKIP);
        assertThat(skip.rows().getFirst().skipReason()).isEqualTo("ON_CONFLICT_SKIP");
    }

    @Test
    void 발행된_Festival과_그_Lineup은_모두_INVALID다() {
        Host host = host(1L, "연세대학교", "연세대");
        Festival published = Festival.builder()
                .host(host).importKey("연세대학교-2026").name("기존 축제")
                .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 2))
                .build();
        ReflectionTestUtils.setField(published, "id", 5L);
        ReflectionTestUtils.setField(published, "publishedAt", Instant.parse("2026-05-01T00:00:00Z"));
        given(hostRepository.findAllByNameIn(anyCollection())).willReturn(List.of(host));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of(published));

        ImportPreviewResponse response = service.previewBundle(
                festivalFile("연세대학교", "", "", ""),
                lineupFile("false", "", ""), null,
                ImportConflictPolicy.UPDATE, Instant.EPOCH);

        assertThat(response.rows()).hasSize(2)
                .allMatch(row -> row.action() == ImportPreviewAction.INVALID);
        assertThat(response.blockers()).anyMatch(blocker ->
                blocker.code().equals("FESTIVAL_ALREADY_PUBLISHED") && blocker.count() == 2);
    }

    @Test
    void INVALID_Festival을_참조하는_Lineup도_INVALID다() {
        given(hostRepository.findAllByNameIn(anyCollection())).willReturn(List.of());
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of());

        ImportPreviewResponse response = service.previewBundle(
                festivalFile("없는대학교", "", "", ""),
                lineupFile("false", "", ""), null,
                ImportConflictPolicy.UPDATE, Instant.EPOCH);

        assertThat(response.rows()).hasSize(2)
                .allMatch(row -> row.action() == ImportPreviewAction.INVALID);
        assertThat(response.rows().get(1).errors()).extracting("code").contains("FESTIVAL_INVALID");
    }

    @Test
    void Artist_alias가_다른_Artist_대표명과_같으면_INVALID다() {
        Artist existing = artist(10L, "Existing");
        given(artistRepository.findAllByNameIn(anyCollection())).willReturn(List.of(existing));
        given(artistAliasRepository.findAllWithArtistByNameIn(anyCollection())).willReturn(List.of());
        String csv = String.join(",", ImportSection.ARTISTS.headers())
                + "\nNew Artist,Existing,BAND,,false\n";

        ImportPreviewResponse response = service.previewSingle(
                ImportSection.ARTISTS, csv("file", "artists.csv", csv),
                ImportConflictPolicy.UPDATE, Instant.EPOCH);

        assertThat(response.rows().getFirst().action()).isEqualTo(ImportPreviewAction.INVALID);
        assertThat(response.rows().getFirst().errors()).extracting("code")
                .contains("ARTIST_DUPLICATE_NAME");
    }

    @Test
    void Artist_alias가_다른_Artist_alias와_같으면_INVALID다() {
        Artist existing = artist(10L, "Existing");
        given(artistRepository.findAllByNameIn(anyCollection())).willReturn(List.of());
        given(artistAliasRepository.findAllWithArtistByNameIn(anyCollection()))
                .willReturn(List.of(alias(existing, "Taken Alias")));
        String csv = String.join(",", ImportSection.ARTISTS.headers())
                + "\nNew Artist,Taken Alias,BAND,,false\n";

        ImportPreviewResponse response = service.previewSingle(
                ImportSection.ARTISTS, csv("file", "artists.csv", csv),
                ImportConflictPolicy.UPDATE, Instant.EPOCH);

        assertThat(response.rows().getFirst().action()).isEqualTo(ImportPreviewAction.INVALID);
        assertThat(response.rows().getFirst().errors()).extracting("code")
                .contains("ARTIST_DUPLICATE_NAME");
    }

    @Test
    void 같은_업로드의_대표명과_alias가_교차하면_두_row_모두_INVALID다() {
        given(artistRepository.findAllByNameIn(anyCollection())).willReturn(List.of());
        given(artistAliasRepository.findAllWithArtistByNameIn(anyCollection())).willReturn(List.of());
        String csv = String.join(",", ImportSection.ARTISTS.headers())
                + "\nAlpha,Beta,BAND,,false\n"
                + "Beta,,BAND,,false\n";

        ImportPreviewResponse response = service.previewSingle(
                ImportSection.ARTISTS, csv("file", "artists.csv", csv),
                ImportConflictPolicy.UPDATE, Instant.EPOCH);

        assertThat(response.rows()).allMatch(row -> row.action() == ImportPreviewAction.INVALID);
        assertThat(response.blockers()).anyMatch(blocker ->
                blocker.code().equals("ARTIST_DUPLICATE_NAME") && blocker.count() == 2);
    }

    @Test
    void 신규_Artist는_CSV값과_무관하게_needsReview가_true다() {
        given(artistRepository.findAllByNameIn(anyCollection())).willReturn(List.of());
        given(artistAliasRepository.findAllWithArtistByNameIn(anyCollection())).willReturn(List.of());
        String csv = String.join(",", ImportSection.ARTISTS.headers())
                + "\nNew Artist,,BAND,,false\n";

        ImportPreviewResponse response = service.previewSingle(
                ImportSection.ARTISTS, csv("file", "artists.csv", csv),
                ImportConflictPolicy.UPDATE, Instant.EPOCH);

        assertThat(response.rows().getFirst().action()).isEqualTo(ImportPreviewAction.CREATE);
        assertThat(response.rows().getFirst().values()).containsEntry("needsReview", true);
    }

    @Test
    void flag가_OK가_아니면_빈_축제_필드도_SKIP이다() {
        String header = String.join(",", ImportSection.FESTIVALS.headers());
        String row = String.join(",",
                "연세대학교-2026", "연세대학교", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "NO_CANDIDATE", "");
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of());

        ImportPreviewResponse response = service.previewSingle(
                ImportSection.FESTIVALS, csv("file", "festivals.csv", header + "\n" + row + "\n"),
                ImportConflictPolicy.UPDATE, Instant.EPOCH);

        assertThat(response.rows().getFirst().action()).isEqualTo(ImportPreviewAction.SKIP);
        assertThat(response.rows().getFirst().skipReason()).isEqualTo("CRAWLER_FLAG_NO_CANDIDATE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "FETCH_FAILED", "EMPTY_BODY", "EXTRACT_FAILED", "MISMATCH", "NO_CANDIDATE", "NO_SOURCE"
    })
    void crawler_실패_flag_6종은_SKIP이다(String flag) {
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of());
        String row = String.join(",",
                "연세대학교-2026", "연세대학교", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", flag, "");

        ImportPreviewResponse response = service.previewSingle(
                ImportSection.FESTIVALS, csv("file", "festivals.csv",
                        String.join(",", ImportSection.FESTIVALS.headers()) + "\n" + row + "\n"),
                ImportConflictPolicy.UPDATE, Instant.EPOCH);

        assertThat(response.rows().getFirst().action()).isEqualTo(ImportPreviewAction.SKIP);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANUAL", "SITEMAP", "SEARCH", "PASTED"})
    void discovery_4종을_허용한다(String discovery) {
        Host host = host(1L, "연세대학교", "연세대");
        given(hostRepository.findAllByNameIn(anyCollection())).willReturn(List.of(host));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of());
        String row = festivalRow("연세대학교", "", "", "").replace(",MANUAL,OK,", "," + discovery + ",OK,");

        ImportPreviewResponse response = service.previewSingle(
                ImportSection.FESTIVALS, csv("file", "festivals.csv",
                        String.join(",", ImportSection.FESTIVALS.headers()) + "\n" + row + "\n"),
                ImportConflictPolicy.UPDATE, Instant.EPOCH);

        assertThat(response.rows().getFirst().action()).isEqualTo(ImportPreviewAction.CREATE);
    }

    private MockMultipartFile festivalFile(
            String hostName, String posterUrl, String imageUrls, String ticketOpenAt
    ) {
        String csv = String.join(",", ImportSection.FESTIVALS.headers()) + "\n"
                + festivalRow(hostName, posterUrl, imageUrls, ticketOpenAt) + "\n";
        return csv("festivals", "festivals.csv", csv);
    }

    private String festivalRow(String hostName, String posterUrl, String imageUrls, String ticketOpenAt) {
        return String.join(",",
                "연세대학교-2026", hostName, "대동제 2026", "2026-05-30", "2026-06-01", "노천극장",
                posterUrl, imageUrls, "설명", "봄|축제", "ALLOWED", "NONE", "FREE",
                ticketOpenAt, "무료", "https://example.com/source", "MANUAL", "OK", "");
    }

    private MockMultipartFile lineupFile(String revealed, String raw, String canonical) {
        String csv = String.join(",", ImportSection.LINEUPS.headers()) + "\n"
                + String.join(",", "연세대학교-2026", "1", "1", raw, canonical, revealed) + "\n";
        return csv("lineups", "lineup.csv", csv);
    }

    private MockMultipartFile artistsFile() {
        String header = String.join(",", ImportSection.ARTISTS.headers());
        return csv("file", "artists.csv", header + "\n"
                + "10CM,십센치,BAND,,false\n"
                + "십센치,권정열,BAND,,false\n"
                + "새 아티스트,새별칭,,,true\n");
    }

    private MockMultipartFile csv(String part, String name, String content) {
        return new MockMultipartFile(part, name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private Host host(Long id, String name, String shortName) {
        Host host = Host.builder().name(name).shortName(shortName).region("서울").build();
        ReflectionTestUtils.setField(host, "id", id);
        return host;
    }

    private Artist artist(Long id, String name) {
        Artist artist = Artist.builder().name(name).needsReview(false).build();
        ReflectionTestUtils.setField(artist, "id", id);
        return artist;
    }

    private ArtistAlias alias(Artist artist, String name) {
        return ArtistAlias.builder().artist(artist).name(name).build();
    }
}
