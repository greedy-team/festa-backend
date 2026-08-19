package com.greedy.festa.importer.service;

import com.greedy.festa.importer.entity.ImportConflictPolicy;
import com.greedy.festa.importer.model.ImportPreviewAction;
import com.greedy.festa.importer.model.ImportSection;
import com.greedy.festa.importer.model.StoredImportPreview;
import com.greedy.festa.importer.model.StoredPreviewRow;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class PreviewJsonCodecTest {

    private final PreviewJsonCodec codec = new PreviewJsonCodec(
            JsonMapper.builder().findAndAddModules().build());

    @Test
    void schemaVersion_1_preview를_직렬화하고_동일하게_복원한다() {
        StoredPreviewRow row = new StoredPreviewRow(
                ImportSection.LINEUPS, 1, "연세대학교-2026", ImportPreviewAction.CREATE,
                ImportConflictPolicy.UPDATE,
                Map.of("revealed", false), Map.of("revealed", "false"),
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
    }

    @Test
    void 지원하지_않는_schemaVersion은_명시적으로_거부한다() {
        String json = "{\"schemaVersion\":2,\"conflictPolicy\":\"UPDATE\",\"rows\":[]}";

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> codec.deserialize(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
    }
}
