package com.greedy.festa.importer.service;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.Lineup;
import com.greedy.festa.artist.entity.ArtistAlias;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.artist.repository.LineupRepository;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.repository.FestivalHashtagRepository;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.repository.HostRepository;
import com.greedy.festa.importer.dto.ImportCommitRequest;
import com.greedy.festa.importer.entity.ImportBatch;
import com.greedy.festa.importer.entity.ImportBatchType;
import com.greedy.festa.importer.entity.ImportCommitAction;
import com.greedy.festa.importer.entity.ImportCommitRow;
import com.greedy.festa.importer.entity.ImportConflictPolicy;
import com.greedy.festa.importer.exception.ImportErrorCode;
import com.greedy.festa.importer.model.ArtistMatchStatus;
import com.greedy.festa.importer.model.ImportPreviewAction;
import com.greedy.festa.importer.model.ImportSection;
import com.greedy.festa.importer.model.PreviewProblem;
import com.greedy.festa.importer.model.StoredImportPreview;
import com.greedy.festa.importer.model.StoredPreviewRow;
import com.greedy.festa.importer.repository.ImportBatchRepository;
import com.greedy.festa.importer.repository.ImportCommitRowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class ImportCommitServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T06:50:00Z");

    @Mock ImportBatchRepository batchRepository;
    @Mock ImportCommitRowRepository auditRepository;
    @Mock HostRepository hostRepository;
    @Mock ArtistRepository artistRepository;
    @Mock ArtistAliasRepository aliasRepository;
    @Mock FestivalRepository festivalRepository;
    @Mock FestivalHashtagRepository hashtagRepository;
    @Mock LineupRepository lineupRepository;

    private PreviewJsonCodec codec;
    private ImportCommitService service;

    @BeforeEach
    void setUp() {
        codec = new PreviewJsonCodec(JsonMapper.builder().findAndAddModules().build());
        service = new ImportCommitService(batchRepository, auditRepository, hostRepository,
                artistRepository, aliasRepository, festivalRepository, hashtagRepository,
                lineupRepository, codec, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void ImportBatch를_pessimistic_lock으로_찾지_못하면_404다() {
        given(batchRepository.findByIdForUpdate(39L)).willReturn(Optional.empty());

        assertError(() -> service.commit(39L, null), ImportErrorCode.IMPORT_NOT_FOUND);

        verify(batchRepository).findByIdForUpdate(39L);
    }

    @Test
    void 이미_commit된_batch는_거부한다() {
        ImportBatch batch = batch("{}", NOW.minusSeconds(60));
        ReflectionTestUtils.setField(batch, "committedAt", NOW.minusSeconds(1));
        given(batchRepository.findByIdForUpdate(39L)).willReturn(Optional.of(batch));

        assertError(() -> service.commit(39L, null), ImportErrorCode.IMPORT_ALREADY_COMMITTED);
    }

    @Test
    void 만료와_손상_preview와_미지원_version을_구분한다() {
        ImportBatch expired = batch("{}", NOW.minusSeconds(3600));
        given(batchRepository.findByIdForUpdate(1L)).willReturn(Optional.of(expired));
        assertError(() -> service.commit(1L, null), ImportErrorCode.IMPORT_EXPIRED);

        given(batchRepository.findByIdForUpdate(2L)).willReturn(Optional.of(batch("not-json", NOW)));
        assertError(() -> service.commit(2L, null), ImportErrorCode.IMPORT_INVALID_PREVIEW);

        String unsupported = "{\"schemaVersion\":2,\"conflictPolicy\":\"UPDATE\",\"rows\":[]}";
        given(batchRepository.findByIdForUpdate(3L)).willReturn(Optional.of(batch(unsupported, NOW)));
        assertError(() -> service.commit(3L, null), ImportErrorCode.IMPORT_UNSUPPORTED_PREVIEW_VERSION);
    }

    @Test
    void 선택되지_않은_INVALID는_막지_않고_선택된_INVALID는_거부한다() {
        StoredPreviewRow invalid = row(ImportSection.ARTISTS, 1, "bad",
                ImportPreviewAction.INVALID, Map.of(), null, null, null,
                ArtistMatchStatus.UNRESOLVED,
                List.of(new PreviewProblem("ARTIST_UNRESOLVED", "", true)), false);
        StoredPreviewRow skip = row(ImportSection.ARTISTS, 2, "existing",
                ImportPreviewAction.SKIP, Map.of("name", "existing", "otherNames", List.of()),
                null, 10L, null, ArtistMatchStatus.MATCHED, List.of(), false);
        Artist artist = artist(10L, "existing");
        prepare(preview(invalid, skip));
        given(artistRepository.findAllById(anyCollection())).willReturn(List.of(artist));
        given(artistRepository.findAllByNameIn(anyCollection())).willReturn(List.of(artist));

        var response = service.commit(39L,
                new ImportCommitRequest(Map.of("artists", List.of(2))));
        assertThat(response.result().artists().created()).isZero();
        assertThat(response.result().artists().updated()).isZero();
        assertThat(response.result().artists().skipped()).isOne();
        assertThat(response.result().artists().failed()).isZero();
        verify(auditRepository).saveAll(any());

        prepare(preview(invalid, skip));
        assertError(() -> service.commit(39L,
                new ImportCommitRequest(Map.of("artists", List.of(1)))),
                ImportErrorCode.IMPORT_UNCOMMITTABLE);
    }

    @Test
    void Lineup은_Festival_단위_일부_선택을_거부한다() {
        StoredPreviewRow first = lineup(1, "festival-2026", 1, 1, 20L, 30L, true);
        StoredPreviewRow second = lineup(2, "festival-2026", 1, 2, 20L, 30L, true);
        prepare(preview(first, second));

        assertError(() -> service.commit(39L,
                new ImportCommitRequest(Map.of("lineups", List.of(1)))),
                ImportErrorCode.IMPORT_INVALID_LINE_SELECTION);
    }

    @Test
    void 빈_selection은_거부한다() {
        StoredPreviewRow row = artistRow(1, ImportPreviewAction.CREATE, null);
        prepare(preview(row));

        assertError(() -> service.commit(39L, new ImportCommitRequest(Map.of())),
                ImportErrorCode.IMPORT_INVALID_LINE_SELECTION);
        verify(auditRepository, never()).saveAll(any());
    }

    @Test
    void 같은_Festival에_INVALID_Lineup이_있으면_정상_Lineup만_선택해도_거부한다() {
        StoredPreviewRow valid = lineup(1, "university-2026", 1, 1, 20L, 30L, true);
        StoredPreviewRow invalid = row(ImportSection.LINEUPS, 2, "university-2026",
                ImportPreviewAction.INVALID,
                Map.of("day", 1, "order", 2, "artistRaw", "bad", "revealed", true),
                null, null, 30L, ArtistMatchStatus.UNRESOLVED,
                List.of(new PreviewProblem("ARTIST_UNRESOLVED", "", true)), true);
        prepare(preview(valid, invalid));

        assertError(() -> service.commit(39L,
                        new ImportCommitRequest(Map.of("lineups", List.of(1)))),
                ImportErrorCode.IMPORT_UNCOMMITTABLE);
        verify(lineupRepository, never()).deleteAllByFestivalId(any());
    }

    @Test
    void 존재하지_않는_section_line은_거부하고_Artist_row_부분선택만_감사한다() {
        StoredPreviewRow first = row(ImportSection.ARTISTS, 1, "first",
                ImportPreviewAction.SKIP, Map.of("name", "first", "otherNames", List.of()),
                null, 10L, null, ArtistMatchStatus.MATCHED, List.of(), false);
        StoredPreviewRow second = row(ImportSection.ARTISTS, 2, "second",
                ImportPreviewAction.SKIP, Map.of("name", "second", "otherNames", List.of()),
                null, 20L, null, ArtistMatchStatus.MATCHED, List.of(), false);
        Artist firstArtist = artist(10L, "first");
        Artist secondArtist = artist(20L, "second");
        prepare(preview(first, second));

        assertError(() -> service.commit(39L,
                new ImportCommitRequest(Map.of("artists", List.of(3)))),
                ImportErrorCode.IMPORT_INVALID_LINE_SELECTION);

        prepare(preview(first, second));
        given(artistRepository.findAllById(anyCollection())).willReturn(List.of(secondArtist));
        given(artistRepository.findAllByNameIn(anyCollection())).willReturn(List.of(secondArtist));
        service.commit(39L, new ImportCommitRequest(Map.of("artists", List.of(2))));

        ArgumentCaptor<List<ImportCommitRow>> audits = ArgumentCaptor.forClass(List.class);
        verify(auditRepository).saveAll(audits.capture());
        assertThat(audits.getValue()).singleElement().satisfies(audit -> {
            assertThat(audit.getLine()).isEqualTo(2);
            assertThat(audit.getArtist()).isSameAs(secondArtist);
        });
        verify(artistRepository, never()).save(firstArtist);
    }

    @Test
    void 신규_Festival과_Artist_dependency가_선택되지_않으면_거부한다() {
        StoredPreviewRow festival = festival(1, ImportPreviewAction.CREATE, null, null);
        StoredPreviewRow artist = artistRow(1, ImportPreviewAction.CREATE, null);
        StoredPreviewRow lineup = lineupWithArtistName(
                1, "university-2026", 1, 1, null, null, true, "new alias");
        prepare(preview(artist, festival, lineup));

        assertError(() -> service.commit(39L,
                new ImportCommitRequest(Map.of("lineups", List.of(1)))),
                ImportErrorCode.IMPORT_INVALID_LINE_SELECTION);
    }

    @Test
    void Artist_Festival_Lineup을_순서대로_생성하고_같은_ID와_committedAt을_감사에_사용한다() {
        Host host = host(1L, "university");
        StoredPreviewRow artist = artistRow(1, ImportPreviewAction.CREATE, null);
        StoredPreviewRow festival = festival(1, ImportPreviewAction.CREATE, null, 1L);
        StoredPreviewRow lineup = lineup(1, "university-2026", 1, 1, null, null, true);
        ImportBatch batch = prepare(preview(artist, festival, lineup));
        given(hostRepository.findAllById(anyCollection())).willReturn(List.of(host));
        given(artistRepository.save(any(Artist.class))).willAnswer(invocation -> {
            Artist saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 20L);
            return saved;
        });
        given(festivalRepository.save(any(Festival.class))).willAnswer(invocation -> {
            Festival saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 30L);
            return saved;
        });

        var response = service.commit(39L, null);

        assertThat(response.committedAt()).isEqualTo(NOW);
        assertThat(response.createdFestivalIds()).containsExactly(30L);
        assertThat(response.result().artists().created()).isOne();
        assertThat(response.result().festivals().created()).isOne();
        assertThat(response.result().lineups().created()).isOne();
        assertThat(response.result().lineups().updated()).isZero();
        assertThat(batch.getCommittedAt()).isEqualTo(NOW);
        assertThat(batch.getPreview()).isNotBlank();

        ArgumentCaptor<List<ImportCommitRow>> audits = ArgumentCaptor.forClass(List.class);
        verify(auditRepository).saveAll(audits.capture());
        assertThat(audits.getValue()).hasSize(3).allMatch(row -> row.getCommittedAt().equals(NOW));
        assertThat(audits.getValue()).extracting(ImportCommitRow::getAction)
                .containsExactly(ImportCommitAction.CREATE, ImportCommitAction.CREATE,
                        ImportCommitAction.CREATE);
        assertThat(audits.getValue().get(1).getPayload().hashtags())
                .containsExactly(" spring ", "spring", "spring ");
        verify(lineupRepository).save(any(Lineup.class));
        var order = inOrder(batchRepository, hostRepository);
        order.verify(batchRepository).findByIdForUpdate(39L);
        order.verify(hostRepository).findAllById(anyCollection());
    }

    @Test
    void preview_이후_발행된_Festival과_Lineup은_stale로_거부한다() {
        Host host = host(1L, "university");
        Festival existing = festivalEntity(30L, host, true);
        StoredPreviewRow festival = festival(1, ImportPreviewAction.UPDATE, 30L, 1L);
        prepare(preview(festival));
        given(hostRepository.findAllById(anyCollection())).willReturn(List.of(host));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of(existing));

        assertError(() -> service.commit(39L, null), ImportErrorCode.IMPORT_PREVIEW_STALE);
        verify(hashtagRepository, never()).deleteAllByFestivalId(any());
        verify(auditRepository, never()).saveAll(any());

        StoredPreviewRow lineup = lineup(1, "university-2026", 1, 1, null, 30L, false);
        prepare(preview(festival, lineup));
        given(hostRepository.findAllById(anyCollection())).willReturn(List.of(host));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of(existing));
        assertError(() -> service.commit(39L, null), ImportErrorCode.IMPORT_PREVIEW_STALE);
    }

    @Test
    void conflictPolicy_SKIP_Festival의_Lineup은_전체_SKIP하고_기존_Lineup을_유지한다() {
        Host host = host(1L, "university");
        Artist artist = artist(20L, "new artist");
        Festival existing = festivalEntity(30L, host, false);
        StoredPreviewRow festival = withPolicy(
                festival(1, ImportPreviewAction.SKIP, 30L, 1L), ImportConflictPolicy.SKIP);
        StoredPreviewRow lineup = withPolicy(
                lineup(1, "university-2026", 1, 1, 20L, 30L, true), ImportConflictPolicy.SKIP);
        prepare(preview(festival, lineup));
        given(hostRepository.findAllById(anyCollection())).willReturn(List.of(host));
        given(artistRepository.findAllById(anyCollection())).willReturn(List.of(artist));
        given(artistRepository.findAllByNameIn(anyCollection())).willReturn(List.of(artist));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of(existing));

        var response = service.commit(39L, null);

        assertThat(response.result().festivals().skipped()).isOne();
        assertThat(response.result().lineups().skipped()).isOne();
        assertThat(response.result().festivals().failed()).isZero();
        assertThat(response.result().lineups().failed()).isZero();
        verify(lineupRepository, never()).deleteAllByFestivalId(any());
        verify(lineupRepository, never()).save(any());
    }

    @Test
    void preview_CREATE가_현재_UPDATE이면_stale로_거부한다() {
        Host host = host(1L, "university");
        StoredPreviewRow row = festival(1, ImportPreviewAction.CREATE, null, 1L);
        prepare(preview(row));
        given(hostRepository.findAllById(anyCollection())).willReturn(List.of(host));
        given(festivalRepository.findAllByImportKeyIn(anyCollection()))
                .willReturn(List.of(festivalEntity(30L, host, false)));

        assertError(() -> service.commit(39L, null), ImportErrorCode.IMPORT_PREVIEW_STALE);
        verify(auditRepository, never()).saveAll(any());
    }

    @Test
    void 추가_Alias가_다른_Artist_소유이면_stale로_거부한다() {
        Artist target = artist(10L, "target");
        Artist other = artist(20L, "other");
        ArtistAlias owned = ArtistAlias.builder().artist(other).name("taken").build();
        StoredPreviewRow update = row(ImportSection.ARTISTS, 1, "target",
                ImportPreviewAction.UPDATE,
                Map.of("name", "target", "otherNames", List.of("taken"), "needsReview", false),
                null, 10L, null, ArtistMatchStatus.MATCHED, List.of(), false);
        prepare(preview(update));
        given(aliasRepository.findAllWithArtistByNameIn(anyCollection())).willReturn(List.of(owned));
        given(artistRepository.findAllById(anyCollection())).willReturn(List.of(target, other));
        given(artistRepository.findAllByNameIn(anyCollection())).willReturn(List.of(target));

        assertError(() -> service.commit(39L, null), ImportErrorCode.IMPORT_PREVIEW_STALE);
        verify(auditRepository, never()).saveAll(any());
    }

    @Test
    void 선택된_두_Artist가_같은_alias를_주장하면_insert_전에_거부한다() {
        StoredPreviewRow first = artistRow(1, ImportPreviewAction.CREATE, null);
        StoredPreviewRow second = row(ImportSection.ARTISTS, 2, "other artist",
                ImportPreviewAction.CREATE,
                Map.of("name", "other artist", "otherNames", List.of("new alias"),
                        "needsReview", false), null, null, null,
                ArtistMatchStatus.NEW, List.of(), false);
        prepare(preview(first, second));

        assertError(() -> service.commit(39L, null), ImportErrorCode.IMPORT_UNCOMMITTABLE);
        verify(artistRepository, never()).save(any());
    }

    @Test
    void 신규_Artist의_대표명이_preview_이후_기존_alias와_충돌하면_stale이다() {
        Artist owner = artist(20L, "owner");
        ArtistAlias alias = ArtistAlias.builder().artist(owner).name("new artist").build();
        prepare(preview(artistRow(1, ImportPreviewAction.CREATE, null)));
        given(aliasRepository.findAllWithArtistByNameIn(anyCollection())).willReturn(List.of(alias));
        given(artistRepository.findAllById(anyCollection())).willReturn(List.of(owner));

        assertError(() -> service.commit(39L, null), ImportErrorCode.IMPORT_PREVIEW_STALE);
        verify(artistRepository, never()).save(any());
    }

    @Test
    void 신규_Artist의_alias가_preview_이후_기존_대표명과_충돌하면_stale이다() {
        Artist owner = artist(20L, "new alias");
        prepare(preview(artistRow(1, ImportPreviewAction.CREATE, null)));
        given(artistRepository.findAllByNameIn(anyCollection())).willReturn(List.of(owner));

        assertError(() -> service.commit(39L, null), ImportErrorCode.IMPORT_PREVIEW_STALE);
        verify(artistRepository, never()).save(any());
    }

    @Test
    void 신규_Artist의_alias가_preview_이후_기존_alias와_충돌하면_stale이다() {
        Artist owner = artist(20L, "owner");
        ArtistAlias alias = ArtistAlias.builder().artist(owner).name("new alias").build();
        prepare(preview(artistRow(1, ImportPreviewAction.CREATE, null)));
        given(aliasRepository.findAllWithArtistByNameIn(anyCollection())).willReturn(List.of(alias));
        given(artistRepository.findAllById(anyCollection())).willReturn(List.of(owner));

        assertError(() -> service.commit(39L, null), ImportErrorCode.IMPORT_PREVIEW_STALE);
        verify(artistRepository, never()).save(any());
    }

    @Test
    void 신규_Artist는_저장_preview값이_false여도_needsReview_true로_생성한다() {
        StoredPreviewRow source = artistRow(1, ImportPreviewAction.CREATE, null);
        Map<String, Object> normalized = new LinkedHashMap<>(source.normalized());
        normalized.put("needsReview", false);
        StoredPreviewRow row = row(ImportSection.ARTISTS, 1, "new artist",
                ImportPreviewAction.CREATE, normalized, null, null, null,
                ArtistMatchStatus.NEW, List.of(), false);
        prepare(preview(row));
        given(artistRepository.save(any(Artist.class))).willAnswer(invocation -> invocation.getArgument(0));

        service.commit(39L, null);

        ArgumentCaptor<Artist> saved = ArgumentCaptor.forClass(Artist.class);
        verify(artistRepository).save(saved.capture());
        assertThat(saved.getValue().isNeedsReview()).isTrue();
    }

    @Test
    void malformed_raw_date가_있는_SKIP_row도_감사하고_commit한다() {
        Host host = host(1L, "university");
        Festival existing = festivalEntity(30L, host, false);
        StoredPreviewRow source = festival(1, ImportPreviewAction.SKIP, 30L, 1L);
        Map<String, String> malformed = new LinkedHashMap<>(source.payload());
        malformed.put("start_date", "not-a-date");
        malformed.put("end_date", "also-not-a-date");
        StoredPreviewRow row = new StoredPreviewRow(source.section(), source.line(),
                source.importKey(), source.action(), source.conflictPolicy(), source.normalized(),
                malformed, source.matchedHostId(), source.matchedArtistId(),
                source.matchedFestivalId(), source.artistMatchStatus(), source.errors(),
                source.warnings(), source.skipReason(), source.revealed(), source.imageUrls(),
                source.ticketOpenAtRaw());
        prepare(preview(row));
        given(hostRepository.findAllById(anyCollection())).willReturn(List.of(host));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of(existing));

        service.commit(39L, null);

        ArgumentCaptor<List<ImportCommitRow>> audits = ArgumentCaptor.forClass(List.class);
        verify(auditRepository).saveAll(audits.capture());
        assertThat(audits.getValue().getFirst().getPayload().startDate()).isNull();
        assertThat(audits.getValue().getFirst().getPayload().endDate()).isNull();
    }

    @Test
    void SKIP_Festival은_발행_상태여도_stale이_아니다() {
        Host host = host(1L, "university");
        Festival published = festivalEntity(30L, host, true);
        StoredPreviewRow row = festival(1, ImportPreviewAction.SKIP, 30L, 1L);
        prepare(preview(row));
        given(hostRepository.findAllById(anyCollection())).willReturn(List.of(host));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of(published));

        var response = service.commit(39L, null);

        assertThat(response.result().festivals().skipped()).isOne();
        assertThat(response.result().festivals().failed()).isZero();
        verify(auditRepository).saveAll(any());
        verify(hashtagRepository, never()).deleteAllByFestivalId(any());
    }

    @Test
    void FestivalHashtag는_값이_있으면_전체교체하고_비어있으면_유지한다() {
        Host host = host(1L, "university");
        Festival existing = festivalEntity(30L, host, false);
        StoredPreviewRow withTags = festival(1, ImportPreviewAction.UPDATE, 30L, 1L);
        prepare(preview(withTags));
        given(hostRepository.findAllById(anyCollection())).willReturn(List.of(host));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of(existing));

        service.commit(39L, null);
        verify(hashtagRepository).deleteAllByFestivalId(30L);
        verify(hashtagRepository).saveAll(any());

        Map<String, Object> emptyNormalized = new LinkedHashMap<>(withTags.normalized());
        emptyNormalized.put("hashtags", List.of());
        StoredPreviewRow withoutTags = row(ImportSection.FESTIVALS, 1, "university-2026",
                ImportPreviewAction.UPDATE, emptyNormalized, 1L, null, 30L,
                null, List.of(), false);
        prepare(preview(withoutTags));
        given(hostRepository.findAllById(anyCollection())).willReturn(List.of(host));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of(existing));

        service.commit(39L, null);
        verify(hashtagRepository, times(1)).deleteAllByFestivalId(30L);
    }

    @Test
    void 중복_Lineup_position은_delete_전에_stale로_거부한다() {
        StoredPreviewRow first = lineup(1, "university-2026", 1, 1, 20L, 30L, true);
        StoredPreviewRow second = lineup(2, "university-2026", 1, 1, 20L, 30L, true);
        Artist artist = artist(20L, "new artist");
        Host host = host(1L, "university");
        Festival festival = festivalEntity(30L, host, false);
        prepare(preview(first, second));
        given(artistRepository.findAllById(anyCollection())).willReturn(List.of(artist));
        given(artistRepository.findAllByNameIn(anyCollection())).willReturn(List.of(artist));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of(festival));

        assertError(() -> service.commit(39L, null), ImportErrorCode.IMPORT_PREVIEW_STALE);
        verify(lineupRepository, never()).deleteAllByFestivalId(any());
    }

    @Test
    void Lineup_insert_실패는_audit과_committedAt_이전에_전파된다() {
        Artist artist = artist(20L, "new artist");
        Host host = host(1L, "university");
        Festival festival = festivalEntity(30L, host, false);
        StoredPreviewRow lineup = lineup(1, "university-2026", 1, 1, 20L, 30L, true);
        ImportBatch batch = prepare(preview(lineup));
        given(artistRepository.findAllById(anyCollection())).willReturn(List.of(artist));
        given(artistRepository.findAllByNameIn(anyCollection())).willReturn(List.of(artist));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of(festival));
        given(lineupRepository.save(any(Lineup.class))).willThrow(new RuntimeException("insert failed"));

        assertThatThrownBy(() -> service.commit(39L, null))
                .isInstanceOf(RuntimeException.class).hasMessage("insert failed");
        verify(lineupRepository).deleteAllByFestivalId(30L);
        verify(auditRepository, never()).saveAll(any());
        assertThat(batch.getCommittedAt()).isNull();
    }

    @Test
    void Hashtag_insert_실패는_audit과_committedAt_이전에_전파된다() {
        Host host = host(1L, "university");
        Festival existing = festivalEntity(30L, host, false);
        StoredPreviewRow update = festival(1, ImportPreviewAction.UPDATE, 30L, 1L);
        ImportBatch batch = prepare(preview(update));
        given(hostRepository.findAllById(anyCollection())).willReturn(List.of(host));
        given(festivalRepository.findAllByImportKeyIn(anyCollection())).willReturn(List.of(existing));
        doThrow(new RuntimeException("hashtag failed"))
                .when(hashtagRepository).saveAll(any());

        assertThatThrownBy(() -> service.commit(39L, null))
                .isInstanceOf(RuntimeException.class).hasMessage("hashtag failed");
        verify(hashtagRepository).deleteAllByFestivalId(30L);
        verify(auditRepository, never()).saveAll(any());
        assertThat(batch.getCommittedAt()).isNull();
    }

    private ImportBatch prepare(StoredImportPreview preview) {
        ImportBatch batch = batch(codec.serialize(preview), NOW);
        given(batchRepository.findByIdForUpdate(39L)).willReturn(Optional.of(batch));
        return batch;
    }

    private StoredImportPreview preview(StoredPreviewRow... rows) {
        return new StoredImportPreview(1, ImportConflictPolicy.UPDATE, List.of(rows));
    }

    private ImportBatch batch(String preview, Instant uploadedAt) {
        ImportBatch batch = ImportBatch.builder().type(ImportBatchType.BUNDLE)
                .fileNames(List.of("test.csv")).onConflict(ImportConflictPolicy.UPDATE)
                .preview(preview).uploadedAt(uploadedAt).build();
        ReflectionTestUtils.setField(batch, "id", 39L);
        return batch;
    }

    private StoredPreviewRow artistRow(int line, ImportPreviewAction action, Long matchedId) {
        return row(ImportSection.ARTISTS, line, "new artist", action,
                Map.of("name", "new artist", "otherNames", List.of("new alias"),
                        "genre", "BAND", "imageUrl", "", "needsReview", true),
                null, matchedId, null,
                matchedId == null ? ArtistMatchStatus.NEW : ArtistMatchStatus.MATCHED,
                List.of(), false);
    }

    private StoredPreviewRow festival(
            int line, ImportPreviewAction action, Long matchedFestivalId, Long hostId
    ) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("importKey", "university-2026");
        normalized.put("hostName", "university");
        normalized.put("name", "festival");
        normalized.put("startDate", LocalDate.of(2026, 5, 1));
        normalized.put("endDate", LocalDate.of(2026, 5, 2));
        normalized.put("hashtags", List.of("spring", "spring"));
        normalized.put("flag", "OK");
        return row(ImportSection.FESTIVALS, line, "university-2026", action,
                normalized, hostId, null, matchedFestivalId, null, List.of(), false);
    }

    private StoredPreviewRow lineup(
            int line, String importKey, int day, int order,
            Long artistId, Long festivalId, boolean revealed
    ) {
        return lineupWithArtistName(line, importKey, day, order, artistId, festivalId,
                revealed, "new artist");
    }

    private StoredPreviewRow lineupWithArtistName(
            int line, String importKey, int day, int order,
            Long artistId, Long festivalId, boolean revealed, String artistName
    ) {
        return row(ImportSection.LINEUPS, line, importKey, ImportPreviewAction.CREATE,
                Map.of("day", day, "order", order, "artistRaw", artistName,
                        "artistCanonical", artistName, "revealed", revealed),
                null, artistId, festivalId,
                revealed && artistId == null ? ArtistMatchStatus.NEW : ArtistMatchStatus.MATCHED,
                List.of(), revealed);
    }

    private StoredPreviewRow row(
            ImportSection section, int line, String importKey, ImportPreviewAction action,
            Map<String, Object> normalized, Long hostId, Long artistId, Long festivalId,
            ArtistMatchStatus matchStatus, List<PreviewProblem> errors, boolean revealed
    ) {
        return new StoredPreviewRow(section, line, importKey, action, ImportConflictPolicy.UPDATE,
                normalized, payload(section, importKey), hostId, artistId, festivalId, matchStatus,
                errors, List.of(), null, revealed, List.of(), null);
    }

    private StoredPreviewRow withPolicy(StoredPreviewRow row, ImportConflictPolicy policy) {
        return new StoredPreviewRow(row.section(), row.line(), row.importKey(), row.action(), policy,
                row.normalized(), row.payload(), row.matchedHostId(), row.matchedArtistId(),
                row.matchedFestivalId(), row.artistMatchStatus(), row.errors(), row.warnings(),
                row.skipReason(), row.revealed(), row.imageUrls(), row.ticketOpenAtRaw());
    }

    private Map<String, String> payload(ImportSection section, String importKey) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("import_key", importKey);
        if (section == ImportSection.FESTIVALS) {
            payload.put("start_date", "2026-05-01");
            payload.put("end_date", "2026-05-02");
            payload.put("hashtags", " spring |spring|spring ");
        }
        return payload;
    }

    private Host host(Long id, String name) {
        Host host = Host.builder().name(name).region("서울").build();
        ReflectionTestUtils.setField(host, "id", id);
        return host;
    }

    private Artist artist(Long id, String name) {
        Artist artist = Artist.builder().name(name).needsReview(false).build();
        ReflectionTestUtils.setField(artist, "id", id);
        return artist;
    }

    private Festival festivalEntity(Long id, Host host, boolean published) {
        Festival festival = Festival.builder().host(host).importKey("university-2026")
                .name("festival").startDate(LocalDate.of(2026, 5, 1))
                .endDate(LocalDate.of(2026, 5, 2)).build();
        ReflectionTestUtils.setField(festival, "id", id);
        if (published) {
            ReflectionTestUtils.setField(festival, "publishedAt", NOW.minusSeconds(1));
        }
        return festival;
    }

    private void assertError(Runnable call, ImportErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOf(FestaException.class)
                .extracting(exception -> ((FestaException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
