package com.greedy.festa.host.service;

import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.dto.HostCreateRequest;
import com.greedy.festa.host.dto.HostResponse;
import com.greedy.festa.host.dto.HostUpdateRequest;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.exception.HostErrorCode;
import com.greedy.festa.host.repository.HostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class HostAdminServiceTest {

    @Mock
    private HostRepository hostRepository;

    @InjectMocks
    private HostAdminService hostAdminService;

    static List<String> 잘못된_이름() {
        return Arrays.asList(null, "", "   ", "연".repeat(101));
    }

    @ParameterizedTest
    @MethodSource("잘못된_이름")
    void 이름이_비었거나_100자를_넘으면_등록에_실패한다(String name) {
        // given
        HostCreateRequest request = createRequest(name, "서울 서대문구");

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> hostAdminService.create(request)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(HostErrorCode.HOST_INVALID_NAME);
    }

    static List<String> 잘못된_지역() {
        return Arrays.asList(null, "", "   ", "서".repeat(51));
    }

    @ParameterizedTest
    @MethodSource("잘못된_지역")
    void 지역이_비었거나_50자를_넘으면_등록에_실패한다(String region) {
        // given
        HostCreateRequest request = createRequest("연세대학교", region);

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> hostAdminService.create(request)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(HostErrorCode.HOST_INVALID_REGION);
    }

    @Test
    void 이미_있는_이름이면_등록에_실패한다() {
        // given
        HostCreateRequest request = createRequest("연세대학교", "서울 서대문구");
        given(hostRepository.existsByName("연세대학교")).willReturn(true);

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> hostAdminService.create(request)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(HostErrorCode.HOST_DUPLICATE_NAME);
    }

    @Test
    void 등록에_성공하면_요청_값이_그대로_담기고_축제_수는_0이다() {
        // given
        HostCreateRequest request = createRequest("연세대학교", "서울 서대문구");
        given(hostRepository.existsByName("연세대학교")).willReturn(false);
        given(hostRepository.save(any(Host.class))).willAnswer(it -> it.getArgument(0));

        // when
        HostResponse response = hostAdminService.create(request);

        // then
        assertSoftly(softly -> {
            softly.assertThat(response.name()).isEqualTo("연세대학교");
            softly.assertThat(response.shortName()).isEqualTo("연세대");
            softly.assertThat(response.region()).isEqualTo("서울 서대문구");
            softly.assertThat(response.festivalCount()).isZero();
        });
    }

    @Test
    void 없는_주최를_수정하면_실패한다() {
        // given
        given(hostRepository.findById(1L)).willReturn(Optional.empty());
        HostUpdateRequest request = new HostUpdateRequest(null, null, null, null, null, null, null);

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> hostAdminService.update(1L, request)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(HostErrorCode.HOST_NOT_FOUND);
    }

    @Test
    void 보내지_않은_필드는_수정되지_않는다() {
        // given
        given(hostRepository.findById(1L)).willReturn(Optional.of(host()));
        given(hostRepository.countFestivalsByHostId(1L)).willReturn(2L);
        HostUpdateRequest request = new HostUpdateRequest(
                null, null, null, null, null, null, "https://yonsei.ac.kr");

        // when
        HostResponse response = hostAdminService.update(1L, request);

        // then
        assertSoftly(softly -> {
            softly.assertThat(response.name()).isEqualTo("연세대학교");
            softly.assertThat(response.shortName()).isEqualTo("연세대");
            softly.assertThat(response.region()).isEqualTo("서울 서대문구");
            softly.assertThat(response.bannerUrl()).isEqualTo("https://cdn.festa.kr/hosts/3/banner.jpg");
            softly.assertThat(response.homepageUrl()).isEqualTo("https://yonsei.ac.kr");
            softly.assertThat(response.festivalCount()).isEqualTo(2L);
        });
    }

    @Test
    void 빈_문자열을_보내면_값이_비워진다() {
        // given
        given(hostRepository.findById(1L)).willReturn(Optional.of(host()));
        given(hostRepository.countFestivalsByHostId(1L)).willReturn(0L);
        HostUpdateRequest request = new HostUpdateRequest(
                null, null, null, null, "", null, null);

        // when
        HostResponse response = hostAdminService.update(1L, request);

        // then
        assertSoftly(softly -> {
            softly.assertThat(response.bannerUrl()).isNull();
            softly.assertThat(response.name()).isEqualTo("연세대학교");
        });
    }

    @Test
    void 축제가_남아_있으면_삭제에_실패한다() {
        // given
        given(hostRepository.findById(1L)).willReturn(Optional.of(host()));
        given(hostRepository.countFestivalsByHostId(1L)).willReturn(3L);

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> hostAdminService.delete(1L)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(HostErrorCode.HOST_HAS_FESTIVALS);
        verify(hostRepository, never()).delete(any());
    }

    @Test
    void 축제가_없으면_삭제된다() {
        // given
        Host host = host();
        given(hostRepository.findById(1L)).willReturn(Optional.of(host));
        given(hostRepository.countFestivalsByHostId(1L)).willReturn(0L);

        // when
        hostAdminService.delete(1L);

        // then
        verify(hostRepository).delete(host);
    }

    static List<Integer> 잘못된_크기() {
        return Arrays.asList(0, -1, 51);
    }

    @ParameterizedTest
    @MethodSource("잘못된_크기")
    void 크기가_1과_50_밖이면_목록_조회에_실패한다(int size) {
        // when
        FestaException 예외 = catchThrowableOfType(
                () -> hostAdminService.findAll(0, size), FestaException.class);

        // then
        assertSoftly(soft -> {
            soft.assertThat(예외.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PAGE_SIZE);
            verify(hostRepository, never()).findAllWithFestivalCount(any());
        });
    }

    @Test
    void 페이지가_음수면_목록_조회에_실패한다() {
        // when
        FestaException 예외 = catchThrowableOfType(
                () -> hostAdminService.findAll(-1, 20), FestaException.class);

        // then
        assertSoftly(soft -> {
            soft.assertThat(예외.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PAGE);
            verify(hostRepository, never()).findAllWithFestivalCount(any());
        });
    }

    @Test
    void 목록_조회는_받은_페이지와_크기를_그대로_리포지토리에_넘긴다() {
        // given
        given(hostRepository.findAllWithFestivalCount(any())).willReturn(Page.empty());

        // when
        hostAdminService.findAll(2, 50);

        // then
        verify(hostRepository).findAllWithFestivalCount(PageRequest.of(2, 50));
    }

    private Host host() {
        return Host.builder()
                .name("연세대학교")
                .shortName("연세대")
                .region("서울 서대문구")
                .bannerUrl("https://cdn.festa.kr/hosts/3/banner.jpg")
                .build();
    }

    private HostCreateRequest createRequest(String name, String region) {
        return new HostCreateRequest(name, "연세대", region, null, null, null, null);
    }
}
