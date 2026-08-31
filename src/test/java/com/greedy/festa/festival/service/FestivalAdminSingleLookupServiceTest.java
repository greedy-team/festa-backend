package com.greedy.festa.festival.service;

import com.greedy.festa.festival.dto.FestivalAdminDetailResponse;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class FestivalAdminSingleLookupServiceTest {

    @Test
    void returnsAllCurrentFestivalManagementValues() {
        FestivalRepository repository = mock(FestivalRepository.class);
        Festival festival = Festival.builder().host(Host.builder().name("한국대학교").region("서울").build())
                .importKey("host-campus-2026").name("대동제")
                .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 2))
                .description("설명").venueName("운동장").latitude(37.0).longitude(127.0).build();
        given(repository.findDetailById(1L)).willReturn(Optional.of(festival));
        given(repository.countLineupsByFestivalId(1L)).willReturn(2L);

        FestivalAdminDetailResponse response = new FestivalAdminService(repository,
                mock(com.greedy.festa.host.repository.HostRepository.class), mock(Clock.class)).findOne(1L);

        assertThat(response.name()).isEqualTo("대동제");
        assertThat(response.importKey()).isEqualTo("host-campus-2026");
        assertThat(response.description()).isEqualTo("설명");
        assertThat(response.lineupCount()).isEqualTo(2L);
        assertThat(response.blockers()).isEmpty();
    }

    @Test
    void missingFestivalUsesExistingErrorContract() {
        FestivalRepository repository = mock(FestivalRepository.class);
        given(repository.findDetailById(1L)).willReturn(Optional.empty());

        FestaException exception = catchThrowableOfType(FestaException.class,
                () -> new FestivalAdminService(repository,
                        mock(com.greedy.festa.host.repository.HostRepository.class), mock(Clock.class)).findOne(1L));

        assertThat(exception.getErrorCode()).isEqualTo(FestivalErrorCode.FESTIVAL_NOT_FOUND);
    }
}
