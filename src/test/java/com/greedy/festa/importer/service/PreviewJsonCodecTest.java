package com.greedy.festa.importer.service;

import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.importer.entity.ImportConflictPolicy;
import com.greedy.festa.importer.exception.ImportErrorCode;
import com.greedy.festa.importer.model.ImportPreviewAction;
import com.greedy.festa.importer.model.ImportSection;
import com.greedy.festa.importer.model.StoredImportPreview;
import com.greedy.festa.importer.model.StoredPreviewRow;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class PreviewJsonCodecTest {

    private final PreviewJsonCodec codec = new PreviewJsonCodec(
            JsonMapper.builder().findAndAddModules().build());

    @Test
    void schemaVersion_1_preview를_직렬화하고_동일하게_복원한다() {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("startDate", "2026-05-15");
        normalized.put("endDate", "2026-05-17");
        normalized.put("ticketOpenAt", "2026-05-01T03:00:00Z");
        normalized.put("revealed", false);
        StoredPreviewRow row = new StoredPreviewRow(
                ImportSection.LINEUPS, 1, "연세대학교-2026", ImportPreviewAction.CREATE,
                ImportConflictPolicy.UPDATE,
                normalized, Map.of("revealed", "false"),
                null, null, null, null, List.of(), List.of(), null,
                false, List.of(), null);
        StoredImportPreview preview = new StoredImportPreview(
                1, ImportConflictPolicy.UPDATE, List.of(row));

        StoredImportPreview restored = codec.deserialize(codec.serialize(preview));

        assertThat(restored.schemaVersion()).isEqualTo(1);
        assertThat(restored.conflictPolicy()).isEqualTo(ImportConflictPolicy.UPDATE);
        assertThat(restored.rows()).hasSize(1);
        assertThat(restored.rows().getFirst().conflictPolicy()).isEqualTo(ImportConflictPolicy.UPDATE);
        assertThat(restored.rows().getFirst().revealed()).isFalse();
        assertThat(restored.rows().getFirst().payload()).containsEntry("revealed", "false");
        assertThat(restored.rows().getFirst().normalized())
                .containsEntry("startDate", "2026-05-15")
                .containsEntry("endDate", "2026-05-17")
                .containsEntry("ticketOpenAt", "2026-05-01T03:00:00Z");
        assertThat(restored.rows().getFirst().normalized().get("startDate")).isInstanceOf(String.class);
        assertThat(restored.rows().getFirst().normalized().get("ticketOpenAt")).isInstanceOf(String.class);
        assertThat(restored.rows().getFirst().normalized()).isEqualTo(normalized);
    }

    @Test
    void 지원하지_않는_schemaVersion은_명시적으로_거부한다() {
        String json = "{\"schemaVersion\":2,\"conflictPolicy\":\"UPDATE\",\"rows\":[]}";

        assertThatThrownBy(() -> codec.deserialize(json))
                .isInstanceOf(FestaException.class)
                .extracting(exception -> ((FestaException) exception).getErrorCode())
                .isEqualTo(ImportErrorCode.IMPORT_UNSUPPORTED_PREVIEW_VERSION);
    }

    @Test
    void 읽을_수_없는_JSON은_IMPORT_INVALID_PREVIEW로_거부한다() {
        assertThatThrownBy(() -> codec.deserialize("not-json"))
                .isInstanceOf(FestaException.class)
                .extracting(exception -> ((FestaException) exception).getErrorCode())
                .isEqualTo(ImportErrorCode.IMPORT_INVALID_PREVIEW);
    }
}
