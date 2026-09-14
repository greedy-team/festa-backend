package com.greedy.festa.global.util;

import com.greedy.festa.global.exception.ErrorCode;
import com.greedy.festa.global.exception.FestaException;

import java.util.Locale;

public class EnumParser {

    private EnumParser() {
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value, ErrorCode errorCode) {
        return parse(type, value, null, errorCode);
    }

    public static <E extends Enum<E>> E parse(
            Class<E> type, String value, E defaultValue, ErrorCode errorCode
    ) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new FestaException(errorCode);
        }
    }
}
