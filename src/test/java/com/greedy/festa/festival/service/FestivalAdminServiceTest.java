package com.greedy.festa.festival.service;

import com.greedy.festa.festival.dto.FestivalPublishResponse;
import com.greedy.festa.festival.dto.FestivalSortType;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class FestivalAdminServiceTest {

    private static final Long 축제_id = 1L;
    private static final Instant 지금 = Instant.parse("2026-08-22T09:00:00Z");
    private static final Instant 예전에_발행한_시각 = Instant.parse("2026-07-01T00:00:00Z");

    @Mock
    private FestivalRepository festivalRepository;

    private FestivalAdminService festivalAdminService;

    @BeforeEach
    void setUp() {
        festivalAdminService = new FestivalAdminService(
                festivalRepository, org.mockito.Mockito.mock(com.greedy.festa.host.repository.HostRepository.class),
                Clock.fixed(지금, ZoneOffset.UTC)
        );
    }

    @Test
    void 조건을_모두_충족하면_고정된_시각으로_발행된다() {
        // given
        Festival festival = 축제(주최(), 37.5509, 127.0743);
        given(festivalRepository.findById(축제_id)).willReturn(Optional.of(festival));
        given(festivalRepository.countLineupsByFestivalId(축제_id)).willReturn(3L);

        // when
        FestivalPublishResponse response = festivalAdminService.publish(축제_id);

        // then
        assertThat(festival.getPublishedAt()).isEqualTo(지금);
        assertThat(response.publishedAt()).isEqualTo(지금);
    }

    @Test
    void 라인업이_0건이면_LINEUP_EMPTY_코드로_막힌다() {
        // given
        Festival festival = 축제(주최(), 37.5509, 127.0743);
        given(festivalRepository.findById(축제_id)).willReturn(Optional.of(festival));
        given(festivalRepository.countLineupsByFestivalId(축제_id)).willReturn(0L);

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> festivalAdminService.publish(축제_id)
        );

        // then
        assertThat(thrown.getErrorCode())
                .isEqualTo(FestivalErrorCode.FESTIVAL_PUBLISH_LINEUP_EMPTY);
        assertThat(festival.getPublishedAt()).isNull();
    }

    @Test
    void 주최가_연결되지_않으면_HOST_NOT_LINKED_코드로_막힌다() {
        // given
        Festival festival = 축제(null, 37.5509, 127.0743);
        given(festivalRepository.findById(축제_id)).willReturn(Optional.of(festival));
        given(festivalRepository.countLineupsByFestivalId(축제_id)).willReturn(3L);

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> festivalAdminService.publish(축제_id)
        );

        // then
        assertThat(thrown.getErrorCode())
                .isEqualTo(FestivalErrorCode.FESTIVAL_PUBLISH_HOST_NOT_LINKED);
    }

    @Test
    void 좌표가_없으면_COORDINATES_MISSING_코드로_막힌다() {
        // given
        Festival festival = 축제(주최(), null, null);
        given(festivalRepository.findById(축제_id)).willReturn(Optional.of(festival));
        given(festivalRepository.countLineupsByFestivalId(축제_id)).willReturn(3L);

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> festivalAdminService.publish(축제_id)
        );

        // then
        assertThat(thrown.getErrorCode())
                .isEqualTo(FestivalErrorCode.FESTIVAL_PUBLISH_COORDINATES_MISSING);
    }

    @Test
    void 없는_축제를_발행하면_FESTIVAL_NOT_FOUND로_막힌다() {
        // given
        given(festivalRepository.findById(축제_id)).willReturn(Optional.empty());

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> festivalAdminService.publish(축제_id)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(FestivalErrorCode.FESTIVAL_NOT_FOUND);
    }

    @Test
    void 발행된_축제를_해제하면_발행_시각이_지워진다() {
        // given
        Festival festival = 축제(주최(), 37.5509, 127.0743);
        festival.publish(예전에_발행한_시각);
        given(festivalRepository.findById(축제_id)).willReturn(Optional.of(festival));

        // when
        FestivalPublishResponse response = festivalAdminService.unpublish(축제_id);

        // then
        assertThat(festival.getPublishedAt()).isNull();
        assertThat(response.publishedAt()).isNull();
    }

    @Test
    void 이미_미발행인_축제를_해제해도_성공한다() {
        // given
        Festival festival = 축제(주최(), 37.5509, 127.0743);
        given(festivalRepository.findById(축제_id)).willReturn(Optional.of(festival));

        // when
        FestivalPublishResponse response = festivalAdminService.unpublish(축제_id);

        // then
        assertThat(response.publishedAt()).isNull();
    }

    @Test
    void 없는_축제를_해제하면_FESTIVAL_NOT_FOUND로_막힌다() {
        // given
        given(festivalRepository.findById(축제_id)).willReturn(Optional.empty());

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> festivalAdminService.unpublish(축제_id)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(FestivalErrorCode.FESTIVAL_NOT_FOUND);
    }

    private void 검수_목록을_조회한다(int page, int size) {
        festivalAdminService.findAll(
                null, null, null, null, null, FestivalSortType.IMPORTED_DESC, page, size
        );
    }

    private Festival 축제(Host host, Double latitude, Double longitude) {
        return Festival.builder()
                .host(host)
                .name("세종대 세종연회")
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    private Host 주최() {
        return Host.builder()
                .name("세종대학교")
                .region("서울 광진구")
                .build();
    }
}
