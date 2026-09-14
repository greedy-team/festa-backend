package com.greedy.festa.festival.service;

import com.greedy.festa.festival.dto.FestivalCoverageResponse;
import com.greedy.festa.festival.dto.FestivalCoverageStatus;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.repository.HostCoverageRow;
import com.greedy.festa.host.repository.HostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.Mockito.lenient;
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

        FestivalCoverageResponse response = service.findCoverage(null, null, 0, 20);

        assertThat(response.hosts().items().getFirst().status()).isEqualTo(FestivalCoverageStatus.NEEDS_CHECK);
    }

    @Test
    void 과거_발행_festival만_있는_host는_needs_check이다() {
        givenRows(row(1L, "가대학교", false, false, null, null));

        FestivalCoverageResponse response = service.findCoverage(2026, null, 0, 20);

        assertThat(response.summary().needsCheck()).isEqualTo(1);
    }

    @Test
    void 미래나_진행중인_발행_festival이_있으면_published이다() {
        givenRows(row(1L, "가대학교", false, true, null, null));

        FestivalCoverageResponse response = service.findCoverage(2026, null, 0, 20);

        assertSoftly(softly -> {
            softly.assertThat(response.summary().published()).isEqualTo(1);
            softly.assertThat(response.hosts().items()).isEmpty();
        });
    }

    @Test
    void 종료_여부와_관계없이_미발행_festival이_있으면_review_pending이다() {
        HostCoverageRow pastUnpublished = row(1L, "과거대학교", true, false, 11L, null);
        HostCoverageRow futureUnpublished = row(2L, "미래대학교", true, true, 12L, null);
        givenRows(pastUnpublished, futureUnpublished);

        FestivalCoverageResponse response = service.findCoverage(2026, null, 0, 20);

        assertThat(response.hosts().items())
                .extracting(item -> item.status())
                .containsOnly(FestivalCoverageStatus.REVIEW_PENDING);
    }

    @Test
    void 발행과_미발행_festival이_동시에_있으면_review_pending이_우선한다() {
        givenRows(row(1L, "가대학교", true, true, 10L, null));

        FestivalCoverageResponse response = service.findCoverage(2026, null, 0, 20);

        assertThat(response.hosts().items().getFirst().status()).isEqualTo(FestivalCoverageStatus.REVIEW_PENDING);
    }

    @Test
    void repository가_선택한_대표_festival과_null_instagram을_응답에_유지한다() {
        HostCoverageRow row = row(1L, "가대학교", true, true, 20L, null);
        given(row.getFestivalName()).willReturn("대표 축제");
        given(row.getStartDate()).willReturn(LocalDate.of(2026, 4, 1));
        given(row.getEndDate()).willReturn(LocalDate.of(2026, 4, 3));
        givenRows(row);

        var item = service.findCoverage(2026, null, 0, 20).hosts().items().getFirst();

        assertSoftly(softly -> {
            softly.assertThat(item.festivalId()).isEqualTo(20L);
            softly.assertThat(item.festivalName()).isEqualTo("대표 축제");
            softly.assertThat(item.instagramUrl()).isNull();
        });
    }

    @Test
    void needs_check의_festival_필드는_null이다() {
        givenRows(row(1L, "가대학교", false, false, null, "https://instagram.com/ga"));

        var item = service.findCoverage(2026, null, 0, 20).hosts().items().getFirst();

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
                2026, "NEEDS_CHECK", 0, 20
        );

        assertSoftly(softly -> {
            softly.assertThat(response.hosts().items()).hasSize(1);
            softly.assertThat(response.hosts().items().getFirst().status())
                    .isEqualTo(FestivalCoverageStatus.NEEDS_CHECK);
            softly.assertThat(response.summary().totalHosts()).isEqualTo(3);
            softly.assertThat(response.summary().published()).isEqualTo(1);
            softly.assertThat(response.summary().reviewPending()).isEqualTo(1);
            softly.assertThat(response.summary().needsCheck()).isEqualTo(1);
            softly.assertThat(response.summary().coverageRate()).isEqualTo(33);
        });
    }

    @Test
    void total_hosts가_0이면_coverage_rate도_0이다() {
        givenRows();

        FestivalCoverageResponse response = service.findCoverage(2026, null, 0, 20);

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

        FestivalCoverageResponse first = service.findCoverage(2026, null, 0, 2);
        FestivalCoverageResponse second = service.findCoverage(2026, null, 1, 2);

        assertSoftly(softly -> {
            softly.assertThat(first.hosts().items()).extracting(item -> item.hostName())
                    .containsExactly("가대학교", "나대학교");
            softly.assertThat(first.hosts().totalElements()).isEqualTo(3);
            softly.assertThat(first.hosts().totalPages()).isEqualTo(2);
            softly.assertThat(first.hosts().hasNext()).isTrue();
            softly.assertThat(first.hosts().hasPrevious()).isFalse();
            softly.assertThat(second.hosts().items()).extracting(item -> item.hostName())
                    .containsExactly("다대학교");
            softly.assertThat(second.hosts().hasNext()).isFalse();
            softly.assertThat(second.hosts().hasPrevious()).isTrue();
        });
    }

    @Test
    void published_status로_조회하면_published_host를_반환한다() {
        givenRows(
                row(1L, "Charlie", false, true, null, null),
                row(2L, "Needs Check Host", false, false, null, null),
                row(3L, "Alpha", false, true, null, null),
                row(4L, "Bravo", false, true, null, null)
        );

        FestivalCoverageResponse first = service.findCoverage(
                2026, "PUBLISHED", 0, 2);
        FestivalCoverageResponse second = service.findCoverage(
                2026, "PUBLISHED", 1, 2);

        assertSoftly(softly -> {
            softly.assertThat(first.hosts().items()).extracting(item -> item.hostName())
                    .containsExactly("Alpha", "Bravo");
            softly.assertThat(second.hosts().items()).extracting(item -> item.hostName())
                    .containsExactly("Charlie");
            softly.assertThat(first.hosts().items()).allMatch(
                    item -> item.status() == FestivalCoverageStatus.PUBLISHED);
            softly.assertThat(first.hosts().totalElements()).isEqualTo(3);
            softly.assertThat(first.hosts().totalPages()).isEqualTo(2);
            softly.assertThat(first.hosts().hasNext()).isTrue();
            softly.assertThat(second.hosts().hasPrevious()).isTrue();
            softly.assertThat(first.summary().published()).isEqualTo(3);
        });
    }

    @Test
    void 지원하지_않는_status는_거부한다() {
        FestaException thrown = catchThrowableOfType(FestaException.class,
                () -> service.findCoverage(2026, "UNKNOWN", 0, 20));

        assertThat(thrown.getErrorCode())
                .isEqualTo(FestivalErrorCode.FESTIVAL_COVERAGE_INVALID_STATUS);
    }

    @Test
    void 빈_status는_필터를_주지_않은_것과_같다() {
        // EnumParser로 옮기며 바뀐 동작이다. 이전에는 null만 보고 isBlank()를 안 봐서
        // ?status= 가 400이었다. 「필터 없음」은 전체가 아니라 PUBLISHED 제외다.
        givenRows(
                row(1L, "Published Host", false, true, null, null),
                row(2L, "Needs Check Host", false, false, null, null)
        );

        FestivalCoverageResponse 결과 = service.findCoverage(2026, "", 0, 20);

        assertThat(결과.hosts().items()).extracting(item -> item.hostName())
                .containsExactly("Needs Check Host");
    }

    @Test
    void status는_대소문자를_가리지_않는다() {
        givenRows(
                row(1L, "Published Host", false, true, null, null),
                row(2L, "Needs Check Host", false, false, null, null)
        );

        FestivalCoverageResponse 결과 = service.findCoverage(2026, "published", 0, 20);

        assertThat(결과.hosts().items()).extracting(item -> item.hostName())
                .containsExactly("Published Host");
    }

    @ParameterizedTest
    @CsvSource({
            "true,true,REVIEW_PENDING",
            "true,false,REVIEW_PENDING",
            "false,true,PUBLISHED",
            "false,false,NEEDS_CHECK"
    })
    void 상태는_두_repository_projection값의_조합으로_판정한다(
            boolean hasUnpublishedFestival,
            boolean hasCurrentFestival,
            FestivalCoverageStatus expected
    ) {
        givenRows(row(1L, "Host", hasUnpublishedFestival, hasCurrentFestival, 10L, null));

        FestivalCoverageResponse response = service.findCoverage(
                2026, expected.name(), 0, 20);

        assertThat(response.hosts().items()).singleElement()
                .extracting(item -> item.status()).isEqualTo(expected);
    }

    @Test
    void year가_null이면_clock의_현재_연도를_사용한다() {
        service.findCoverage(null, null, 0, 20);

        verify(hostRepository).findCoverageRows(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2026, 1, 1));
    }

    @Test
    void 최소_연도_2026은_허용한다() {
        service.findCoverage(2026, null, 0, 20);

        verify(hostRepository).findCoverageRows(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2026, 1, 1));
    }

    @ParameterizedTest
    @CsvSource({"-1,20,INVALID_PAGE", "0,0,INVALID_PAGE_SIZE", "0,51,INVALID_PAGE_SIZE"})
    void page와_size_범위를_검증한다(int page, int size, CommonErrorCode expected) {
        FestaException thrown = catchThrowableOfType(FestaException.class,
                () -> service.findCoverage(2026, null, page, size));

        assertThat(thrown.getErrorCode()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"2025", "2028"})
    void 허용_범위를_벗어난_year는_거부한다(int year) {
        FestaException thrown = catchThrowableOfType(FestaException.class,
                () -> service.findCoverage(year, null, 0, 20));

        assertThat(thrown.getErrorCode())
                .isEqualTo(FestivalErrorCode.FESTIVAL_COVERAGE_INVALID_YEAR);
    }

    @Test
    void 지정한_year의_범위와_asia_seoul의_오늘을_repository에_전달한다() {
        service.findCoverage(2027, null, 0, 20);

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
        lenient().when(row.getHasUnpublishedFestival()).thenReturn(hasUnpublishedFestival);
        lenient().when(row.getHasCurrentFestival()).thenReturn(hasCurrentFestival);
        given(row.getFestivalId()).willReturn(festivalId);
        given(row.getInstagramUrl()).willReturn(instagramUrl);
        return row;
    }
}
