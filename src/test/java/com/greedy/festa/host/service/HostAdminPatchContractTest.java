package com.greedy.festa.host.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.dto.HostResponse;
import com.greedy.festa.host.dto.HostUpdateRequest;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.exception.HostErrorCode;
import com.greedy.festa.host.repository.HostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class HostAdminPatchContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HostAdminService service;
    private Host host;

    @BeforeEach
    void setUp() {
        HostRepository repository = mock(HostRepository.class);
        service = new HostAdminService(repository);
        host = Host.builder().name("기존 학교").shortName("기존대").region("서울")
                .logoUrl("old-logo").bannerUrl("old-banner").homepageUrl("old-homepage")
                .instagramUrl("https://old.example").build();
        given(repository.findById(1L)).willReturn(Optional.of(host));
    }

    @Test
    void omittedRequiredFieldIsRejected() throws Exception {
        FestaException exception = catchThrowableOfType(FestaException.class,
                () -> service.update(1L, objectMapper.readValue(
                        "{\"instagramUrl\":\"https://old.example\"}", HostUpdateRequest.class)));
        assertThat(exception.getErrorCode()).isEqualTo(HostErrorCode.HOST_INVALID_NAME);
    }

    @Test
    void suppliedFieldsChangeIndependently() throws Exception {
        HostResponse response = service.update(1L, objectMapper.readValue(
                "{\"name\":\"새 학교\",\"instagramUrl\":\"https://new.example\",\"logoUrl\":\"new-logo\",\"bannerUrl\":\"\"}",
                HostUpdateRequest.class));

        assertThat(response.name()).isEqualTo("새 학교");
        assertThat(response.logoUrl()).isEqualTo("new-logo");
        assertThat(response.bannerUrl()).isNull();
        assertThat(response.shortName()).isEqualTo("기존대");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankNullableStringDeletesValue(String value) throws Exception {
        HostUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"기존 학교\",\"instagramUrl\":\"https://old.example\",\"logoUrl\":\"" + value + "\"}", HostUpdateRequest.class);

        assertThat(service.update(1L, request).logoUrl()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankRequiredRegionIsRejected(String value) throws Exception {
        HostUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"기존 학교\",\"instagramUrl\":\"https://old.example\",\"region\":\"" + value + "\"}", HostUpdateRequest.class);

        FestaException exception = catchThrowableOfType(
                FestaException.class, () -> service.update(1L, request));
        assertThat(exception.getErrorCode()).isEqualTo(HostErrorCode.HOST_INVALID_REGION);
    }

    @Test
    void explicitNullInstagramUrlDeletesValue() throws Exception {
        HostUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"기존 학교\",\"instagramUrl\":null}", HostUpdateRequest.class);
        assertThat(service.update(1L, request).instagramUrl()).isNull();
    }

    @Test
    void explicitNullRequiredNameIsRejected() throws Exception {
        HostUpdateRequest request = objectMapper.readValue(
                "{\"name\":null,\"instagramUrl\":\"https://old.example\"}", HostUpdateRequest.class);

        FestaException exception = catchThrowableOfType(
                FestaException.class, () -> service.update(1L, request));
        assertThat(exception.getErrorCode()).isEqualTo(HostErrorCode.HOST_INVALID_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"logoUrl", "bannerUrl", "homepageUrl"})
    void optionalUrlOmissionKeepsExistingValue(String field) throws Exception {
        service.update(1L, objectMapper.readValue(
                "{\"name\":\"기존 학교\",\"instagramUrl\":\"https://old.example\"}",
                HostUpdateRequest.class));

        assertThat(url(field)).isEqualTo(oldUrl(field));
    }

    @ParameterizedTest
    @ValueSource(strings = {"logoUrl", "bannerUrl", "homepageUrl"})
    void optionalUrlValueReplacesExistingValue(String field) throws Exception {
        service.update(1L, objectMapper.readValue(
                "{\"name\":\"기존 학교\",\"instagramUrl\":\"https://old.example\",\""
                        + field + "\":\"new-value\"}", HostUpdateRequest.class));

        assertThat(url(field)).isEqualTo("new-value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"logoUrl", "bannerUrl", "homepageUrl"})
    void optionalUrlEmptyStringDeletesValue(String field) throws Exception {
        service.update(1L, objectMapper.readValue(
                "{\"name\":\"기존 학교\",\"instagramUrl\":\"https://old.example\",\""
                        + field + "\":\"\"}", HostUpdateRequest.class));

        assertThat(url(field)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"logoUrl", "bannerUrl", "homepageUrl"})
    void optionalUrlExplicitNullDeletesValue(String field) throws Exception {
        service.update(1L, objectMapper.readValue(
                "{\"name\":\"기존 학교\",\"instagramUrl\":\"https://old.example\",\""
                        + field + "\":null}", HostUpdateRequest.class));

        assertThat(url(field)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"logoUrl", "bannerUrl", "homepageUrl"})
    void optionalUrlWhitespaceDeletesValue(String field) throws Exception {
        service.update(1L, objectMapper.readValue(
                "{\"name\":\"기존 학교\",\"instagramUrl\":\"https://old.example\",\""
                        + field + "\":\"   \"}", HostUpdateRequest.class));

        assertThat(url(field)).isNull();
    }

    private String url(String field) {
        return switch (field) {
            case "logoUrl" -> host.getLogoUrl();
            case "bannerUrl" -> host.getBannerUrl();
            case "homepageUrl" -> host.getHomepageUrl();
            default -> throw new IllegalArgumentException(field);
        };
    }

    private String oldUrl(String field) {
        return switch (field) {
            case "logoUrl" -> "old-logo";
            case "bannerUrl" -> "old-banner";
            case "homepageUrl" -> "old-homepage";
            default -> throw new IllegalArgumentException(field);
        };
    }
}
