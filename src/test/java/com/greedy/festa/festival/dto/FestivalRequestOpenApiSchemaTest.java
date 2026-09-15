package com.greedy.festa.festival.dto;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class FestivalRequestOpenApiSchemaTest {

    @ParameterizedTest
    @MethodSource("requestTypes")
    void 요청_enum에는_UNKNOWN을_노출하지_않는다(Class<?> requestType) {
        ResolvedSchema resolved = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(requestType));
        Schema<?> requestSchema = resolved.referencedSchemas.get(requestType.getSimpleName());

        assertThat(propertyEnum(requestSchema, "externalVisitor"))
                .containsExactly("ALLOWED", "CONDITIONAL", "DENIED");
        assertThat(propertyEnum(requestSchema, "verification"))
                .containsExactly("NONE", "STUDENT_ID", "PRE_BOOKING", "INVITATION", "OTHER");
        assertThat(propertyEnum(requestSchema, "ticketType"))
                .containsExactly("FREE", "PAID");
    }

    private List<String> propertyEnum(Schema<?> schema, String property) {
        return schema.getProperties().get(property).getEnum().stream()
                .map(String::valueOf)
                .toList();
    }

    private static Stream<Class<?>> requestTypes() {
        return Stream.of(FestivalCreateRequest.class, FestivalUpdateRequest.class);
    }
}
