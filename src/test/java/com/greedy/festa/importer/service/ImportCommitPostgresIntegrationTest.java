package com.greedy.festa.importer.service;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.repository.FestivalHashtagRepository;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.repository.HostRepository;
import com.greedy.festa.importer.entity.ImportBatch;
import com.greedy.festa.importer.entity.ImportBatchType;
import com.greedy.festa.importer.entity.ImportConflictPolicy;
import com.greedy.festa.importer.exception.ImportErrorCode;
import com.greedy.festa.importer.model.ArtistMatchStatus;
import com.greedy.festa.importer.model.ImportPreviewAction;
import com.greedy.festa.importer.model.ImportSection;
import com.greedy.festa.importer.model.StoredImportPreview;
import com.greedy.festa.importer.model.StoredPreviewRow;
import com.greedy.festa.importer.repository.ImportBatchRepository;
import com.greedy.festa.importer.repository.ImportCommitRowRepository;
import com.greedy.festa.lineup.entity.Lineup;
import com.greedy.festa.lineup.repository.LineupRepository;
import com.greedy.festa.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SuppressWarnings("NonAsciiCharacters")
@SpringBootTest
@ActiveProfiles("test")
@Import(ImportCommitPostgresIntegrationTest.FixedClockConfig.class)
class ImportCommitPostgresIntegrationTest extends PostgresTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-19T06:50:00Z");

    @Autowired ImportCommitService service;
    @Autowired PreviewJsonCodec codec;
    @Autowired ImportBatchRepository batchRepository;
    @MockitoSpyBean ImportCommitRowRepository auditRepository;
    @Autowired HostRepository hostRepository;
    @Autowired ArtistRepository artistRepository;
    @Autowired ArtistAliasRepository aliasRepository;
    @Autowired FestivalRepository festivalRepository;
    @Autowired FestivalHashtagRepository hashtagRepository;
    @Autowired LineupRepository lineupRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        auditRepository.deleteAll();
        lineupRepository.deleteAll();
        hashtagRepository.deleteAll();
        batchRepository.deleteAll();
        aliasRepository.deleteAll();
        festivalRepository.deleteAll();
        artistRepository.deleteAll();
        hostRepository.deleteAll();
    }

    @Test
    void 미발행_Festival의_기존_Lineup을_삭제하고_새_Lineup으로_전체_교체한다() {
        Host host = hostRepository.save(Host.builder().name("university").region("SEOUL").build());
        Artist oldArtist = artistRepository.save(Artist.builder().name("old").needsReview(false).build());
        Artist newArtist = artistRepository.save(Artist.builder().name("new").needsReview(false).build());
        Festival festival = festivalRepository.save(festival(host));
        lineupRepository.save(Lineup.builder().festival(festival).artist(oldArtist)
                .day(1).displayOrder(1).build());
        ImportBatch batch = saveBatch(preview(
                lineup(1, festival.getId(), newArtist.getId(), 1),
                lineup(2, festival.getId(), newArtist.getId(), 2)));

        service.commit(batch.getId(), null);

        assertThat(lineupRepository.findAll())
                .extracting(Lineup::getDisplayOrder).containsExactlyInAnyOrder(1, 2);
        assertThat(lineupRepository.findAll()).allMatch(lineup ->
                lineup.getArtist().getId().equals(newArtist.getId()));
        assertThat(auditRepository.count()).isEqualTo(2);
        assertThat(batchRepository.findById(batch.getId()).orElseThrow().getCommittedAt())
                .isEqualTo(NOW);
    }

    @Test
    void 감사_저장_실패는_Artist_Festival_Lineup과_committedAt을_모두_rollback한다() {
        Host host = hostRepository.save(Host.builder().name("university").region("SEOUL").build());
        ImportBatch batch = saveBatch(preview(
                newArtist(1), newFestival(1, host.getId()), newLineup(1)));
        doThrow(new RuntimeException("audit failed")).when(auditRepository).saveAll(any());

        assertThatThrownBy(() -> service.commit(batch.getId(), null))
                .isInstanceOf(RuntimeException.class).hasMessage("audit failed");

        assertThat(artistRepository.count()).isZero();
        assertThat(festivalRepository.count()).isZero();
        assertThat(lineupRepository.count()).isZero();
        assertThat(auditRepository.count()).isZero();
        assertThat(batchRepository.findById(batch.getId()).orElseThrow().getCommittedAt()).isNull();
    }

    @Test
    void 동일_ImportBatch의_동시_commit은_한_요청만_성공한다() throws Exception {
        Artist artist = artistRepository.save(Artist.builder().name("existing").needsReview(false).build());
        ImportBatch batch = saveBatch(preview(skipArtist(1, artist.getId())));
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> commitResult(batch.getId(), start));
            var second = executor.submit(() -> commitResult(batch.getId(), start));
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", ImportErrorCode.IMPORT_ALREADY_COMMITTED.name());
        }
        assertThat(auditRepository.count()).isOne();
        assertThat(batchRepository.findById(batch.getId()).orElseThrow().getCommittedAt())
                .isEqualTo(NOW);
    }

    @Test
    void CREATE_Festival의_좌표를_PostgreSQL에_저장한다() {
        Host host = hostRepository.save(Host.builder().name("university").region("SEOUL").build());
        ImportBatch batch = saveBatch(preview(newFestival(1, host.getId())));

        service.commit(batch.getId(), null);

        Festival saved = festivalRepository.findAll().getFirst();
        assertThat(saved.getImportKey()).isEqualTo("university-main-campus-2026");
        assertThat(saved.getLatitude()).isEqualTo(37.5665);
        assertThat(saved.getLongitude()).isEqualTo(126.978);
    }

    @Test
    void UPDATE_Festival의_좌표를_PostgreSQL에_반영한다() {
        Host host = hostRepository.save(Host.builder().name("university").region("SEOUL").build());
        Festival festival = festivalRepository.save(festival(host));
        ImportBatch batch = saveBatch(preview(row(
                ImportSection.FESTIVALS, 1, "university-main-campus-2026",
                ImportPreviewAction.UPDATE,
                Map.of("hostName", "university", "name", "festival",
                        "startDate", LocalDate.of(2026, 5, 1),
                        "endDate", LocalDate.of(2026, 5, 2),
                        "latitude", 35.1796, "longitude", 129.0756,
                        "hashtags", List.of(), "flag", "OK"),
                host.getId(), null, festival.getId(), null, false)));

        service.commit(batch.getId(), null);

        Festival updated = festivalRepository.findById(festival.getId()).orElseThrow();
        assertThat(updated.getLatitude()).isEqualTo(35.1796);
        assertThat(updated.getLongitude()).isEqualTo(129.0756);
    }

    @Test
    void 레거시_입장_정책의_빈_normalized를_commit하면_원본_문자열을_보존한다() {
        Host host = hostRepository.save(Host.builder().name("university").region("SEOUL").build());
        Long festivalId = jdbcTemplate.queryForObject("""
                INSERT INTO festival
                    (host_id, import_key, name, start_date, end_date,
                     external_visitor, verification, ticket_type, created_at, updated_at)
                VALUES (?, 'university-main-campus-2026', 'legacy festival',
                        DATE '2026-05-01', DATE '2026-05-02',
                        'OUTSIDER_NEW', 'FACE_SCAN', 'EARLY_BIRD', NOW(), NOW())
                RETURNING id
                """, Long.class, host.getId());
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("hostName", "university");
        normalized.put("name", "updated festival");
        normalized.put("startDate", LocalDate.of(2026, 5, 1));
        normalized.put("endDate", LocalDate.of(2026, 5, 2));
        normalized.put("externalVisitorPolicy", null);
        normalized.put("verificationMethod", null);
        normalized.put("ticketType", null);
        normalized.put("hashtags", List.of());
        normalized.put("flag", "OK");
        ImportBatch batch = saveBatch(preview(row(
                ImportSection.FESTIVALS, 1, "university-main-campus-2026",
                ImportPreviewAction.UPDATE, normalized,
                host.getId(), null, festivalId, null, false)));

        service.commit(batch.getId(), null);

        assertThat(jdbcTemplate.queryForMap("""
                SELECT name, external_visitor, verification, ticket_type
                FROM festival WHERE id = ?
                """, festivalId))
                .containsEntry("name", "updated festival")
                .containsEntry("external_visitor", "OUTSIDER_NEW")
                .containsEntry("verification", "FACE_SCAN")
                .containsEntry("ticket_type", "EARLY_BIRD");
    }

    private String commitResult(Long batchId, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            service.commit(batchId, null);
            return "SUCCESS";
        } catch (FestaException e) {
            return e.getErrorCode().name();
        }
    }

    private ImportBatch saveBatch(StoredImportPreview preview) {
        return batchRepository.save(ImportBatch.builder()
                .type(ImportBatchType.BUNDLE).fileNames(List.of("test.csv"))
                .onConflict(ImportConflictPolicy.UPDATE).preview(codec.serialize(preview))
                .uploadedAt(NOW).build());
    }

    private StoredImportPreview preview(StoredPreviewRow... rows) {
        return new StoredImportPreview(1, ImportConflictPolicy.UPDATE, List.of(rows));
    }

    private StoredPreviewRow lineup(int line, Long festivalId, Long artistId, int order) {
        return row(ImportSection.LINEUPS, line, "university-main-campus-2026",
                ImportPreviewAction.CREATE,
                Map.of("day", 1, "order", order, "artistRaw", "new",
                        "artistCanonical", "new", "revealed", true),
                null, artistId, festivalId, ArtistMatchStatus.MATCHED, true);
    }

    private StoredPreviewRow newArtist(int line) {
        return row(ImportSection.ARTISTS, line, "new", ImportPreviewAction.CREATE,
                Map.of("name", "new", "otherNames", List.of(), "genre", "BAND",
                        "imageUrl", "", "needsReview", false),
                null, null, null, ArtistMatchStatus.NEW, false);
    }

    private StoredPreviewRow skipArtist(int line, Long artistId) {
        return row(ImportSection.ARTISTS, line, "existing", ImportPreviewAction.SKIP,
                Map.of("name", "existing", "otherNames", List.of()),
                null, artistId, null, ArtistMatchStatus.MATCHED, false);
    }

    private StoredPreviewRow newFestival(int line, Long hostId) {
        return row(ImportSection.FESTIVALS, line, "university-main-campus-2026",
                ImportPreviewAction.CREATE,
                Map.of("hostName", "university", "name", "festival",
                        "startDate", LocalDate.of(2026, 5, 1),
                        "endDate", LocalDate.of(2026, 5, 2),
                        "latitude", 37.5665, "longitude", 126.978,
                        "hashtags", List.of(), "flag", "OK"),
                hostId, null, null, null, false);
    }

    private StoredPreviewRow newLineup(int line) {
        return row(ImportSection.LINEUPS, line, "university-main-campus-2026",
                ImportPreviewAction.CREATE,
                Map.of("day", 1, "order", 1, "artistRaw", "new",
                        "artistCanonical", "new", "revealed", true),
                null, null, null, ArtistMatchStatus.NEW, true);
    }

    private StoredPreviewRow row(
            ImportSection section, int line, String importKey, ImportPreviewAction action,
            Map<String, Object> normalized, Long hostId, Long artistId, Long festivalId,
            ArtistMatchStatus matchStatus, boolean revealed
    ) {
        return new StoredPreviewRow(section, line, importKey, action, ImportConflictPolicy.UPDATE,
                normalized, Map.of("import_key", importKey), hostId, artistId, festivalId,
                matchStatus, List.of(), List.of(), null, revealed, List.of(), null);
    }

    private Festival festival(Host host) {
        return Festival.builder().host(host).importKey("university-main-campus-2026").name("festival")
                .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 2)).build();
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
