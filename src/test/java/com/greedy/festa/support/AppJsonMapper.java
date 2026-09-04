package com.greedy.festa.support;

import com.greedy.festa.global.config.JacksonCoercionConfig;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public final class AppJsonMapper {

    private AppJsonMapper() {
    }

    public static ObjectMapper create() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonCoercionConfig().customize(builder);
        return builder.build();
    }
}
