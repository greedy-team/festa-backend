package com.greedy.festa.host.service;

import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.dto.HostResponse;
import com.greedy.festa.host.dto.HostUpdateRequest;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.exception.HostErrorCode;
import com.greedy.festa.host.repository.HostRepository;
import com.greedy.festa.support.AppJsonMapper;
import com.greedy.festa.support.fixture.HostFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class HostAdminPatchContractTest {

    private final ObjectMapper objectMapper = AppJsonMapper.create();
    private HostAdminService service;
    private Host host;

    @BeforeEach
    void setUp() {
        HostRepository repository = mock(HostRepository.class);
        service = new HostAdminService(repository);
        host = HostFixture.host("기존 학교").shortName("기존대")
                .logoUrl("old-logo").bannerUrl("old-banner").homepageUrl("old-homepage")
                .instagramUrl("https://old.example").build();
        given(repository.findById(1L)).willReturn(Optional.of(host));
    }

    @Test
    void omittedRequiredNameIsRejected() throws Exception {
        FestaException exception = catchThrowableOfType(FestaException.class,
                () -> service.update(1L, objectMapper.readValue(
                        "{\"region\":\"서울\"}", HostUpdateRequest.class)));
        assertThat(exception.getErrorCode()).isEqualTo(HostErrorCode.HOST_INVALID_NAME);
    }

    @Test
    void omittedRequiredRegionIsRejected() throws Exception {
        FestaException exception = catchThrowableOfType(FestaException.class,
                () -> service.update(1L, objectMapper.readValue(
                        "{\"name\":\"기존 학교\"}", HostUpdateRequest.class)));
        assertThat(exception.getErrorCode()).isEqualTo(HostErrorCode.HOST_INVALID_REGION);
    }

    @Test
    void suppliedFieldsChangeIndependently() throws Exception {
        HostResponse response = service.update(1L, objectMapper.readValue(
                "{\"name\":\"새 학교\",\"region\":\"서울\",\"instagramUrl\":\"https://new.example\","
                        + "\"logoUrl\":\"new-logo\",\"bannerUrl\":\"\"}",
                HostUpdateRequest.class));

        assertThat(response.name()).isEqualTo("새 학교");
        assertThat(response.region()).isEqualTo("서울");
        assertThat(response.logoUrl()).isEqualTo("new-logo");
        assertThat(response.bannerUrl()).isNull();
        assertThat(response.instagramUrl()).isEqualTo("https://new.example");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankNullableStringDeletesValue(String value) throws Exception {
        HostUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"기존 학교\",\"region\":\"서울\",\"instagramUrl\":\"https://old.example\",\"logoUrl\":\""
                        + value + "\"}", HostUpdateRequest.class);

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

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankRequiredNameIsRejected(String value) throws Exception {
        HostUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"" + value + "\",\"region\":\"서울\",\"instagramUrl\":\"https://old.example\"}", HostUpdateRequest.class);

        FestaException exception = catchThrowableOfType(
                FestaException.class, () -> service.update(1L, request));
        assertThat(exception.getErrorCode()).isEqualTo(HostErrorCode.HOST_INVALID_NAME);
    }

    @Test
    void explicitNullInstagramUrlDeletesValue() throws Exception {
        HostUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"기존 학교\",\"region\":\"서울\",\"instagramUrl\":null}", HostUpdateRequest.class);
        assertThat(service.update(1L, request).instagramUrl()).isNull();
    }

    @Test
    void explicitNullRequiredNameIsRejected() throws Exception {
        HostUpdateRequest request = objectMapper.readValue(
                "{\"name\":null,\"region\":\"서울\",\"instagramUrl\":\"https://old.example\"}", HostUpdateRequest.class);

        FestaException exception = catchThrowableOfType(
                FestaException.class, () -> service.update(1L, request));
        assertThat(exception.getErrorCode()).isEqualTo(HostErrorCode.HOST_INVALID_NAME);
    }

    @Test
    void explicitNullRequiredRegionIsRejected() throws Exception {
        HostUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"기존 학교\",\"region\":null,\"instagramUrl\":\"https://old.example\"}", HostUpdateRequest.class);

        FestaException exception = catchThrowableOfType(
                FestaException.class, () -> service.update(1L, request));
        assertThat(exception.getErrorCode()).isEqualTo(HostErrorCode.HOST_INVALID_REGION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"shortName", "logoUrl", "bannerUrl", "homepageUrl"})
    void optionalStringOmissionDeletesValue(String field) throws Exception {
        service.update(1L, objectMapper.readValue(
                "{\"name\":\"기존 학교\",\"region\":\"서울\",\"instagramUrl\":\"https://old.example\"}",
                HostUpdateRequest.class));

        assertThat(url(field)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"shortName", "logoUrl", "bannerUrl", "homepageUrl"})
    void optionalStringValueReplacesExistingValue(String field) throws Exception {
        service.update(1L, objectMapper.readValue(
                "{\"name\":\"기존 학교\",\"region\":\"서울\",\"instagramUrl\":\"https://old.example\",\""
                        + field + "\":\"new-value\"}", HostUpdateRequest.class));

        assertThat(url(field)).isEqualTo("new-value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"shortName", "logoUrl", "bannerUrl", "homepageUrl"})
    void optionalStringEmptyStringDeletesValue(String field) throws Exception {
        service.update(1L, objectMapper.readValue(
                "{\"name\":\"기존 학교\",\"region\":\"서울\",\"instagramUrl\":\"https://old.example\",\""
                        + field + "\":\"\"}", HostUpdateRequest.class));

        assertThat(url(field)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"shortName", "logoUrl", "bannerUrl", "homepageUrl"})
    void optionalStringExplicitNullDeletesValue(String field) throws Exception {
        service.update(1L, objectMapper.readValue(
                "{\"name\":\"기존 학교\",\"region\":\"서울\",\"instagramUrl\":\"https://old.example\",\""
                        + field + "\":null}", HostUpdateRequest.class));

        assertThat(url(field)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"shortName", "logoUrl", "bannerUrl", "homepageUrl"})
    void optionalStringWhitespaceDeletesValue(String field) throws Exception {
        service.update(1L, objectMapper.readValue(
                "{\"name\":\"기존 학교\",\"region\":\"서울\",\"instagramUrl\":\"https://old.example\",\""
                        + field + "\":\"   \"}", HostUpdateRequest.class));

        assertThat(url(field)).isNull();
    }

    private String url(String field) {
        return switch (field) {
            case "shortName" -> host.getShortName();
            case "logoUrl" -> host.getLogoUrl();
            case "bannerUrl" -> host.getBannerUrl();
            case "homepageUrl" -> host.getHomepageUrl();
            default -> throw new IllegalArgumentException(field);
        };
    }
}
