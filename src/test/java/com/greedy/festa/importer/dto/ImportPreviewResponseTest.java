package com.greedy.festa.importer.dto;

import com.greedy.festa.importer.entity.ImportBatch;
import com.greedy.festa.importer.entity.ImportBatchType;
import com.greedy.festa.importer.entity.ImportConflictPolicy;
import com.greedy.festa.importer.model.ArtistMatchStatus;
import com.greedy.festa.importer.model.ImportPreviewAction;
import com.greedy.festa.importer.model.ImportSection;
import com.greedy.festa.importer.model.PreviewProblem;
import com.greedy.festa.importer.model.StoredPreviewRow;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class ImportPreviewResponseTest {

    @Test
    void API_JSON은_확정된_summary와_row_필드명을_사용한다() throws Exception {
        Instant uploadedAt = Instant.parse("2026-08-19T00:00:00Z");
        ImportBatch batch = ImportBatch.builder()
                .type(ImportBatchType.ARTISTS).fileNames(List.of("artists.csv"))
                .onConflict(ImportConflictPolicy.UPDATE).preview("{}")
                .uploadedAt(uploadedAt).build();
        ReflectionTestUtils.setField(batch, "id", 1L);
        StoredPreviewRow row = new StoredPreviewRow(
                ImportSection.ARTISTS, 1, "새 아티스트", ImportPreviewAction.CREATE,
                ImportConflictPolicy.UPDATE,
                Map.of("name", "새 아티스트"), Map.of("name", "새 아티스트"),
                null, null, null, ArtistMatchStatus.NEW, List.of(), List.of(), null,
                null, List.of(), null);

        String json = JsonMapper.builder().findAndAddModules().build()
                .writeValueAsString(ImportPreviewResponse.of(batch, List.of(row)));
        JsonNode root = JsonMapper.builder().findAndAddModules().build().readTree(json);

        assertThat(root.has("importId")).isTrue();
        assertThat(root.get("summary").has("total")).isTrue();
        assertThat(root.get("summary").has("toCreate")).isTrue();
        assertThat(root.get("summary").has("toUpdate")).isTrue();
        assertThat(root.get("summary").has("toSkip")).isTrue();
        assertThat(root.get("summary").has("invalid")).isTrue();
        assertThat(root.get("rows").get(0).has("line")).isTrue();
        assertThat(root.get("rows").get(0).has("matchedArtistId")).isTrue();
        assertThat(root.get("rows").get(0).has("artistMatchStatus")).isTrue();
        assertThat(root.toString()).doesNotContain("rowNumber");
    }

    @Test
    void blocker_JSON은_type이_아닌_code를_사용하고_ARTIST_UNRESOLVED는_raw값을_보여준다() throws Exception {
        ImportBatch batch = ImportBatch.builder()
                .type(ImportBatchType.LINEUPS).fileNames(List.of("lineups.csv"))
                .onConflict(ImportConflictPolicy.UPDATE).preview("{}")
                .uploadedAt(Instant.EPOCH).build();
        ReflectionTestUtils.setField(batch, "id", 2L);
        StoredPreviewRow row = new StoredPreviewRow(
                ImportSection.LINEUPS, 1, "festival-key", ImportPreviewAction.INVALID,
                ImportConflictPolicy.UPDATE,
                Map.of("artistCanonical", "", "artistRaw", "확인할 아티스트"), Map.of(),
                null, null, null, ArtistMatchStatus.UNRESOLVED,
                List.of(new PreviewProblem("ARTIST_UNRESOLVED", "확인이 필요합니다", true)),
                List.of(), null, null, List.of(), null);

        String json = JsonMapper.builder().findAndAddModules().build()
                .writeValueAsString(ImportPreviewResponse.of(batch, List.of(row)));
        JsonNode blocker = JsonMapper.builder().findAndAddModules().build()
                .readTree(json).get("blockers").get(0);

        assertThat(blocker.get("code").asText()).isEqualTo("ARTIST_UNRESOLVED");
        assertThat(blocker.has("type")).isFalse();
        assertThat(blocker.get("values").get(0).asText()).isEqualTo("확인할 아티스트");
    }
}
