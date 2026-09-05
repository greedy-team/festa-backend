package com.greedy.festa.artist.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greedy.festa.artist.dto.ArtistResponse;
import com.greedy.festa.artist.dto.ArtistUpdateRequest;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.support.fixture.ArtistFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ArtistAdminPatchContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ArtistRepository artistRepository;
    private ArtistAdminService service;
    private Artist artist;

    @BeforeEach
    void setUp() {
        artistRepository = mock(ArtistRepository.class);
        ArtistAliasRepository aliasRepository = mock(ArtistAliasRepository.class);
        service = new ArtistAdminService(artistRepository, aliasRepository);
        artist = ArtistFixture.artist("기존 이름").genre(ArtistGenre.DANCE)
                .instagramUrl("https://old.example").build();
        given(artistRepository.findById(1L)).willReturn(Optional.of(artist));
        given(aliasRepository.findByArtistId(1L)).willReturn(List.of());
    }

    @Test
    void omittedRequiredFieldIsRejected() throws Exception {
        FestaException exception = catchThrowableOfType(FestaException.class,
                () -> service.update(1L, objectMapper.readValue(
                        "{\"instagramUrl\":\"https://old.example\"}", ArtistUpdateRequest.class)));
        assertThat(exception.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_INVALID_NAME);
    }

    @Test
    void suppliedFieldsChangeIndependently() throws Exception {
        ArtistResponse response = service.update(1L, objectMapper.readValue(
                "{\"name\":\"새 이름\",\"instagramUrl\":\"https://new.example\"}",
                ArtistUpdateRequest.class));

        assertThat(response.name()).isEqualTo("새 이름");
        assertThat(response.instagramUrl()).isEqualTo("https://new.example");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankNullableStringDeletesValue(String value) throws Exception {
        ArtistUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"기존 이름\",\"instagramUrl\":\"" + value + "\"}", ArtistUpdateRequest.class);

        assertThat(service.update(1L, request).instagramUrl()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankRequiredNameIsRejected(String value) throws Exception {
        ArtistUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"" + value + "\",\"instagramUrl\":\"https://old.example\"}", ArtistUpdateRequest.class);

        FestaException exception = catchThrowableOfType(
                FestaException.class, () -> service.update(1L, request));
        assertThat(exception.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_INVALID_NAME);
    }

    @Test
    void explicitNullNullableStringDeletesValue() throws Exception {
        ArtistUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"기존 이름\",\"instagramUrl\":null}", ArtistUpdateRequest.class);

        assertThat(service.update(1L, request).instagramUrl()).isNull();
    }

    @Test
    void omittedNullableStringDeletesValue() throws Exception {
        ArtistUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"기존 이름\"}", ArtistUpdateRequest.class);

        assertThat(service.update(1L, request).instagramUrl()).isNull();
    }

    @Test
    void explicitNullRequiredNameIsRejected() throws Exception {
        ArtistUpdateRequest request = objectMapper.readValue(
                "{\"name\":null,\"instagramUrl\":\"https://old.example\"}", ArtistUpdateRequest.class);

        FestaException exception = catchThrowableOfType(
                FestaException.class, () -> service.update(1L, request));
        assertThat(exception.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_INVALID_NAME);
    }

    @Test
    void omittedGenreKeepsExistingValue() throws Exception {
        ArtistUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"기존 이름\"}", ArtistUpdateRequest.class);

        assertThat(service.update(1L, request).genre()).isEqualTo(ArtistGenre.DANCE);
    }

    @Test
    void explicitNullGenreKeepsExistingValue() throws Exception {
        ArtistUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"기존 이름\",\"genre\":null}", ArtistUpdateRequest.class);

        assertThat(service.update(1L, request).genre()).isEqualTo(ArtistGenre.DANCE);
    }

    @Test
    void omittedNeedsReviewKeepsExistingValue() throws Exception {
        artist.markNeedsReview();

        ArtistUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"기존 이름\"}", ArtistUpdateRequest.class);

        assertThat(service.update(1L, request).needsReview()).isTrue();
    }
}
