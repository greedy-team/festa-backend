package com.greedy.festa.global.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * 관리자 수정은 전체 교체이고 빈 문자열은 "비우기"를 뜻한다.
 * 날짜·시각·숫자는 Jackson 기본값이 이미 빈 문자열을 null로 바꾸지만,
 * enum만 예외를 던져 같은 요청이 400으로 튕긴다. 그 한 자리를 맞춘다.
 */
@Configuration
public class JacksonCoercionConfig implements JsonMapperBuilderCustomizer {

    @Override
    public void customize(JsonMapper.Builder builder) {
        builder.withCoercionConfig(LogicalType.Enum, config ->
                config.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull));
    }
}
