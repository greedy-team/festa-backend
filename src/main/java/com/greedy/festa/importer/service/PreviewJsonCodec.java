package com.greedy.festa.importer.service;

import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.importer.exception.ImportErrorCode;
import com.greedy.festa.importer.model.StoredImportPreview;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class PreviewJsonCodec {

    private final ObjectMapper objectMapper;

    public PreviewJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(StoredImportPreview preview) {
        try {
            return objectMapper.writeValueAsString(preview);
        } catch (Exception e) {
            throw new IllegalStateException("preview JSON 직렬화에 실패했습니다", e);
        }
    }

    public StoredImportPreview deserialize(String json) {
        StoredImportPreview preview;
        try {
            preview = objectMapper.readValue(json, StoredImportPreview.class);
        } catch (Exception e) {
            throw new FestaException(ImportErrorCode.IMPORT_INVALID_PREVIEW);
        }
        if (preview.schemaVersion() != StoredImportPreview.CURRENT_SCHEMA_VERSION) {
            throw new FestaException(ImportErrorCode.IMPORT_UNSUPPORTED_PREVIEW_VERSION);
        }
        return preview;
    }
}
