package com.greedy.festa.importer.service;

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
        try {
            StoredImportPreview preview = objectMapper.readValue(json, StoredImportPreview.class);
            if (preview.schemaVersion() != StoredImportPreview.CURRENT_SCHEMA_VERSION) {
                throw new UnsupportedPreviewVersionException(
                        "지원하지 않는 preview schemaVersion입니다: " + preview.schemaVersion());
            }
            return preview;
        } catch (Exception e) {
            if (e instanceof UnsupportedPreviewVersionException unsupported) {
                throw unsupported;
            }
            throw new InvalidPreviewException("preview JSON을 읽을 수 없습니다", e);
        }
    }

    public static class UnsupportedPreviewVersionException extends IllegalArgumentException {
        public UnsupportedPreviewVersionException(String message) {
            super(message);
        }
    }

    public static class InvalidPreviewException extends IllegalArgumentException {
        public InvalidPreviewException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
