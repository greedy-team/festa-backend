package com.greedy.festa.festival.entity;

import jakarta.persistence.AttributeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

abstract class UnknownSafeEnumConverter<E extends Enum<E> & UnknownSafeEnum> implements AttributeConverter<E, String> {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Class<E> enumType;
    private final E unknownValue;
    private final Map<String, E> valuesByName;

    protected UnknownSafeEnumConverter(Class<E> enumType) {
        this.enumType = enumType;
        this.valuesByName = Arrays.stream(enumType.getEnumConstants())
                .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));
        this.unknownValue = valuesByName.values().stream()
                .filter(UnknownSafeEnum::isUnknown)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("UNKNOWN sentinel is required"));
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        E value = valuesByName.get(dbData);
        if (value != null && !value.isUnknown()) {
            return value;
        }

        log.warn("Unknown {} database value: {}", enumType.getSimpleName(), dbData);
        return unknownValue;
    }
}
