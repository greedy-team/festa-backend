package com.greedy.festa.festival.entity;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ExternalVisitorPolicyConverter
        extends UnknownSafeEnumConverter<ExternalVisitorPolicy> {

    public ExternalVisitorPolicyConverter() {
        super(ExternalVisitorPolicy.class, ExternalVisitorPolicy.UNKNOWN);
    }
}
