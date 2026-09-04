package com.greedy.festa.global.util;

import com.greedy.festa.global.exception.ErrorCode;
import com.greedy.festa.global.exception.FestaException;

import java.util.Objects;

public class LikePatternUtils {

    private LikePatternUtils() {
    }

    /**
     * Escapes PostgreSQL LIKE metacharacters so the input is matched literally.
     * Backslashes must be escaped first so the escapes added for '%' and '_' are not escaped again.
     */
    public static String escape(String value) {
        String nonNullValue = Objects.requireNonNull(value, "value must not be null");
        return nonNullValue.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    public static String normalizeOptionalPattern(String raw, int maxLength, ErrorCode onTooLong) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return normalizeAndEscape(raw, maxLength, onTooLong);
    }

    public static String normalizeRequiredQuery(String raw, int maxLength, ErrorCode onInvalid) {
        if (raw == null || raw.isBlank()) {
            throw new FestaException(onInvalid);
        }
        return normalize(raw, maxLength, onInvalid);
    }

    private static String normalizeAndEscape(String raw, int maxLength, ErrorCode onTooLong) {
        return escape(normalize(raw, maxLength, onTooLong));
    }

    private static String normalize(String raw, int maxLength, ErrorCode onTooLong) {
        String normalized = raw.trim();
        if (normalized.length() > maxLength) {
            throw new FestaException(onTooLong);
        }
        return normalized;
    }
}
