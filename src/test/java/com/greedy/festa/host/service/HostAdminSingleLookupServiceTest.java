package com.greedy.festa.host.service;

import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.dto.HostResponse;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.exception.HostErrorCode;
import com.greedy.festa.host.repository.HostRepository;
import com.greedy.festa.support.fixture.HostFixture;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class HostAdminSingleLookupServiceTest {

    @Test
    void returnsCurrentHostManagementValues() {
        HostRepository repository = mock(HostRepository.class);
        Host host = HostFixture.host("한국대학교").shortName("한대")
                .homepageUrl("homepage").build();
        given(repository.findById(1L)).willReturn(Optional.of(host));
        given(repository.countFestivalsByHostId(1L)).willReturn(4L);

        HostResponse response = new HostAdminService(repository).findOne(1L);

        assertThat(response.name()).isEqualTo("한국대학교");
        assertThat(response.shortName()).isEqualTo("한대");
        assertThat(response.homepageUrl()).isEqualTo("homepage");
        assertThat(response.festivalCount()).isEqualTo(4L);
    }

    @Test
    void missingHostUsesExistingErrorContract() {
        HostRepository repository = mock(HostRepository.class);
        given(repository.findById(1L)).willReturn(Optional.empty());

        FestaException exception = catchThrowableOfType(FestaException.class,
                () -> new HostAdminService(repository).findOne(1L));

        assertThat(exception.getErrorCode()).isEqualTo(HostErrorCode.HOST_NOT_FOUND);
    }
}
