package com.greedy.festa.festival.entity;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class VerificationMethodConverter
        extends UnknownSafeEnumConverter<VerificationMethod> {

    public VerificationMethodConverter() {
        super(VerificationMethod.class);
    }
}
