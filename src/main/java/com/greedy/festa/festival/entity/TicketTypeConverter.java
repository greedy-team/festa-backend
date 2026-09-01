package com.greedy.festa.festival.entity;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TicketTypeConverter extends UnknownSafeEnumConverter<TicketType> {

    public TicketTypeConverter() {
        super(TicketType.class, TicketType.UNKNOWN);
    }
}
