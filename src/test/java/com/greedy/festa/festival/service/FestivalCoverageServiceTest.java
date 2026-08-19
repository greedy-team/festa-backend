package com.greedy.festa.festival.service;

import com.greedy.festa.festival.dto.FestivalCoverageResponse;
import com.greedy.festa.festival.dto.FestivalCoverageStatus;
import com.greedy.festa.festival.exception.FestivalCoverageErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.repository.HostCoverageRow;
import com.greedy.festa.host.repository.HostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FestivalCoverageServiceTest {

    private static final Instant KST_NEW_YEAR = Instant.parse("2025-12-31T15:30:00Z");

    @Mock
    private HostRepository hostRepository;

    private FestivalCoverageService service;

    @BeforeEach
    void setUp() {
        service = new FestivalCoverageService(
                hostRepository,
                Clock.fixed(KST_NEW_YEAR, ZoneOffset.UTC)
        );
    }

    @Test
    void festival이_없는_host는_needs_check이다() {
        HostCoverageRow row = row(1L, "가대학교", false, false, null, null);
        given(hostRepository.findCoverageRows(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2026, 1, 1)
        )).willReturn(List.of(row));

        FestivalCoverageResponse response = service.findCoverage(null, null, PageRequest.of(0, 20));

        assertThat(response.items().getFirst().status()).isEqualTo(FestivalCoverageStatus.NEEDS_CHECK);
    }

    @Test
    void 과거_발행_festival만_있는_host는_needs_check이다() {
        givenRows(row(1L, "가대학교", false, false, null, null));

        FestivalCoverageResponse response = service.findCoverage(2026, null, PageRequest.of(0, 20));

        assertThat(response.summary().needsCheck()).isEqualTo(1);
    }

    @Test
    void 미래나_진행중인_발행_festival이_있으면_published이다() {
        givenRows(row(1L, "가대학교", false, true, null, null));

        FestivalCoverageResponse response = service.findCoverage(2026, null, PageRequest.of(0, 20));

        assertSoftly(softly -> {
            softly.assertThat(response.summary().published()).isEqualTo(1);
            softly.assertThat(response.items()).isEmpty();
        });
    }

    @Test
    void 종료_여부와_관계없이_미발행_festival이_있으면_review_pending이다() {
        HostCoverageRow pastUnpublished = row(1L, "과거대학교", true, false, 11L, null);
        HostCoverageRow futureUnpublished = row(2L, "미래대학교", true, true, 12L, null);
        givenRows(pastUnpublished, futureUnpublished);

        FestivalCoverageResponse response = service.findCoverage(2026, null, PageRequest.of(0, 20));

        assertThat(response.items())
                .extracting(item -> item.status())
                .containsOnly(FestivalCoverageStatus.REVIEW_PENDING);
    }

    @Test
    void 발행과_미발행_festival이_동시에_있으면_review_pending이_우선한다() {
        givenRows(row(1L, "가대학교", true, true, 10L, null));

        FestivalCoverageResponse response = service.findCoverage(2026, null, PageRequest.of(0, 20));

        assertThat(response.items().getFirst().status()).isEqualTo(FestivalCoverageStatus.REVIEW_PENDING);
    }

    @Test
    void repository가_선택한_대표_festival과_null_instagram을_응답에_유지한다() {
        HostCoverageRow row = row(1L, "가대학교", true, true, 20L, null);
        given(row.getFestivalName()).willReturn("대표 축제");
        given(row.getStartDate()).willReturn(LocalDate.of(2026, 4, 1));
        given(row.getEndDate()).willReturn(LocalDate.of(2026, 4, 3));
        givenRows(row);

        var item = service.findCoverage(2026, null, PageRequest.of(0, 20)).items().getFirst();

        assertSoftly(softly -> {
            softly.assertThat(item.festivalId()).isEqualTo(20L);
            softly.assertThat(item.festivalName()).isEqualTo("대표 축제");
            softly.assertThat(item.instagramUrl()).isNull();
        });
    }

    @Test
    void needs_check의_festival_필드는_null이다() {
        givenRows(row(1L, "가대학교", false, false, null, "https://instagram.com/ga"));

        var item = service.findCoverage(2026, null, PageRequest.of(0, 20)).items().getFirst();

        assertSoftly(softly -> {
            softly.assertThat(item.festivalId()).isNull();
            softly.assertThat(item.festivalName()).isNull();
            softly.assertThat(item.startDate()).isNull();
            softly.assertThat(item.endDate()).isNull();
        });
    }

    @Test
    void status_필터는_items에만_적용하고_summary는_전체를_유지한다() {
        givenRows(
                row(1L, "가대학교", true, false, 10L, null),
                row(2L, "나대학교", false, false, null, null),
                row(3L, "다대학교", false, true, null, null)
        );

        FestivalCoverageResponse response = service.findCoverage(
                2026, "NEEDS_CHECK", PageRequest.of(0, 20)
        );

        assertSoftly(softly -> {
            softly.assertThat(response.items()).hasSize(1);
            softly.assertThat(response.items().getFirst().status())
                    .isEqualTo(FestivalCoverageStatus.NEEDS_CHECK);
            softly.assertThat(response.summary().totalHosts()).isEqualTo(3);
            softly.assertThat(response.summary().published()).isEqualTo(1);
            softly.assertThat(response.summary().reviewPending()).isEqualTo(1);
            softly.assertThat(response.summary().needsCheck()).isEqualTo(1);
            softly.assertThat(response.summary().coverageRate()).isEqualTo(67);
        });
    }

    @Test
    void total_hosts가_0이면_coverage_rate도_0이다() {
        givenRows();

        FestivalCoverageResponse response = service.findCoverage(2026, null, PageRequest.of(0, 20));

        assertSoftly(softly -> {
            softly.assertThat(response.summary().totalHosts()).isZero();
            softly.assertThat(response.summary().coverageRate()).isZero();
        });
    }

    @Test
    void review_pending을_먼저_정렬하고_같은_상태는_host_name으로_정렬하고_페이지한다() {
        givenRows(
                row(1L, "다대학교", false, false, null, null),
                row(2L, "나대학교", true, false, 20L, null),
                row(3L, "가대학교", true, false, 30L, null)
        );

        FestivalCoverageResponse first = service.findCoverage(2026, null, PageRequest.of(0, 2));
        FestivalCoverageResponse second = service.findCoverage(2026, null, PageRequest.of(1, 2));

        assertSoftly(softly -> {
            softly.assertThat(first.items()).extracting(item -> item.hostName())
                    .containsExactly("가대학교", "나대학교");
            softly.assertThat(first.totalElements()).isEqualTo(3);
            softly.assertThat(first.totalPages()).isEqualTo(2);
            softly.assertThat(first.hasNext()).isTrue();
            softly.assertThat(first.hasPrevious()).isFalse();
            softly.assertThat(second.items()).extracting(item -> item.hostName())
                    .containsExactly("다대학교");
            softly.assertThat(second.hasNext()).isFalse();
            softly.assertThat(second.hasPrevious()).isTrue();
        });
    }

    @Test
    void 지원하지_않는_status는_거부한다() {
        FestaException thrown = catchThrowableOfType(
                FestaException.class,
                () -> service.findCoverage(2026, "PUBLISHED", PageRequest.of(0, 20))
        );

        assertThat(thrown.getErrorCode())
                .isEqualTo(FestivalCoverageErrorCode.FESTIVAL_COVERAGE_INVALID_STATUS);
    }

    @Test
    void 지정한_year의_범위와_asia_seoul의_오늘을_repository에_전달한다() {
        service.findCoverage(2027, null, PageRequest.of(0, 20));

        verify(hostRepository).findCoverageRows(
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2028, 1, 1),
                LocalDate.of(2026, 1, 1)
        );
    }

    private void givenRows(HostCoverageRow... rows) {
        given(hostRepository.findCoverageRows(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2026, 1, 1)
        )).willReturn(List.of(rows));
    }

    private HostCoverageRow row(
            Long hostId,
            String hostName,
            boolean hasUnpublishedFestival,
            boolean hasCurrentFestival,
            Long festivalId,
            String instagramUrl
    ) {
        HostCoverageRow row = mock(HostCoverageRow.class);
        given(row.getHostId()).willReturn(hostId);
        given(row.getHostName()).willReturn(hostName);
        given(row.getHasUnpublishedFestival()).willReturn(hasUnpublishedFestival);
        if (!hasUnpublishedFestival) {
            given(row.getHasCurrentFestival()).willReturn(hasCurrentFestival);
        }
        given(row.getFestivalId()).willReturn(festivalId);
        given(row.getInstagramUrl()).willReturn(instagramUrl);
        return row;
    }
}
