package com.greedy.festa.festival.entity;

import jakarta.persistence.AttributeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class UnknownSafeEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Class<E> enumType;
    private final E unknownValue;

    protected UnknownSafeEnumConverter(Class<E> enumType, E unknownValue) {
        this.enumType = enumType;
        this.unknownValue = unknownValue;
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

        for (E value : enumType.getEnumConstants()) {
            if (value.name().equals(dbData)) {
                return value;
            }
        }

        log.warn("Unknown {} database value: {}", enumType.getSimpleName(), dbData);
        return unknownValue;
    }
}
