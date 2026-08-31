package com.greedy.festa.festival.service;

import com.greedy.festa.festival.dto.FestivalCreateRequest;
import com.greedy.festa.festival.dto.FestivalResponse;
import com.greedy.festa.festival.dto.FestivalUpdateRequest;
import com.greedy.festa.festival.entity.ExternalVisitorPolicy;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.entity.FestivalPublishBlocker;
import com.greedy.festa.festival.entity.TicketType;
import com.greedy.festa.festival.entity.VerificationMethod;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.ErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.exception.HostErrorCode;
import com.greedy.festa.host.repository.HostRepository;
import com.greedy.festa.lineup.entity.Lineup;
import com.greedy.festa.lineup.repository.LineupRepository;
import com.greedy.festa.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SuppressWarnings("NonAsciiCharacters")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class FestivalAdminServiceTest extends PostgresTestSupport {

    private static final LocalDate 시작일 = LocalDate.of(2026, 5, 20);
    private static final LocalDate 종료일 = LocalDate.of(2026, 5, 22);
    private static final Instant 티켓_오픈 = Instant.parse("2026-05-01T00:00:00Z");
    private static final Instant 발행_시각 = Instant.parse("2026-05-10T00:00:00Z");
    private static final String 임포트_키 = "테스트대학교-본교-2026";

    @Autowired
    private FestivalRepository festivalRepository;

    @Autowired
    private LineupRepository lineupRepository;

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private EntityManager em;

    private FestivalAdminService festivalAdminService;
    private Host 주최;

    @BeforeEach
    void setUp() {
        festivalAdminService = new FestivalAdminService(festivalRepository, lineupRepository, hostRepository);
        주최 = em.merge(Host.builder().name("테스트대학교").region("서울 광진구").build());
        반영한다();
    }

    @Nested
    class 등록 {

        @Test
        @DisplayName("17개 필드가 서로 다른 값으로 빠짐없이 매핑된다 - 위치 인자가 뒤바뀌면 여기서 걸린다")
        void 모든_필드가_제자리에_매핑된다() {
            FestivalResponse response =
                    festivalAdminService.create(등록요청(주최.getId(), 임포트_키, "세종연회", 시작일, 종료일));
            반영한다();

            Festival saved = festivalRepository.findById(response.festivalId()).orElseThrow();
            assertThat(saved.getHost().getId()).isEqualTo(주최.getId());
            assertThat(saved.getImportKey()).isEqualTo(임포트_키);
            assertThat(saved.getName()).isEqualTo("세종연회");
            assertThat(saved.getStartDate()).isEqualTo(시작일);
            assertThat(saved.getEndDate()).isEqualTo(종료일);
            assertThat(saved.getPosterUrl()).isEqualTo("https://example.com/poster.png");
            assertThat(saved.getDescription()).isEqualTo("설명입니다");
            assertThat(saved.getVenueName()).isEqualTo("대운동장");
            assertThat(saved.getAddress()).isEqualTo("서울 광진구 능동로 209");
            assertThat(saved.getLatitude()).isEqualTo(37.5509);
            assertThat(saved.getLongitude()).isEqualTo(127.0743);
            assertThat(saved.getExternalVisitor()).isEqualTo(ExternalVisitorPolicy.CONDITIONAL);
            assertThat(saved.getVerification()).isEqualTo(VerificationMethod.STUDENT_ID);
            assertThat(saved.getTicketType()).isEqualTo(TicketType.FREE);
            assertThat(saved.getTicketOpenAt()).isEqualTo(티켓_오픈);
            assertThat(saved.getAdmissionNote()).isEqualTo("학생증 지참");
            assertThat(saved.getInstagramUrl()).isEqualTo("https://instagram.com/sejong");
        }

        @Test
        void 크롤러_메타와_발행_시각은_비어서_저장된다() {
            FestivalResponse response =
                    festivalAdminService.create(등록요청(주최.getId(), 임포트_키, "세종연회", 시작일, 종료일));
            반영한다();

            Festival saved = festivalRepository.findById(response.festivalId()).orElseThrow();
            assertThat(saved.getAdmissionRaw()).isNull();
            assertThat(saved.getDiscovery()).isNull();
            assertThat(saved.getCrawlFlag()).isNull();
            assertThat(saved.getSourceUrl()).isNull();
            assertThat(saved.getImportedAt()).isNull();
            assertThat(saved.getPublishedAt()).isNull();
        }

        @Test
        void 라인업이_없으므로_응답이_LINEUP_EMPTY를_알려준다() {
            FestivalResponse response =
                    festivalAdminService.create(등록요청(주최.getId(), 임포트_키, "세종연회", 시작일, 종료일));

            assertThat(response.blockers()).containsExactly(FestivalPublishBlocker.LINEUP_EMPTY);
        }

        @Test
        void 이름이_공백뿐이면_거부한다() {
            FestivalCreateRequest 요청 = 등록요청(주최.getId(), 임포트_키, "   ", 시작일, 종료일);

            assertThat(에러코드(() -> festivalAdminService.create(요청)))
                    .isEqualTo(FestivalErrorCode.FESTIVAL_INVALID_NAME);
        }

        @Test
        void 시작일이_없으면_거부한다() {
            FestivalCreateRequest 요청 = 등록요청(주최.getId(), 임포트_키, "세종연회", null, 종료일);

            assertThat(에러코드(() -> festivalAdminService.create(요청)))
                    .isEqualTo(FestivalErrorCode.FESTIVAL_INVALID_START_DATE);
        }

        @Test
        void 종료일이_없으면_거부한다() {
            FestivalCreateRequest 요청 = 등록요청(주최.getId(), 임포트_키, "세종연회", 시작일, null);

            assertThat(에러코드(() -> festivalAdminService.create(요청)))
                    .isEqualTo(FestivalErrorCode.FESTIVAL_INVALID_END_DATE);
        }

        @Test
        void 시작일이_종료일보다_늦으면_거부한다() {
            FestivalCreateRequest 요청 = 등록요청(주최.getId(), 임포트_키, "세종연회", 종료일.plusDays(1), 종료일);

            assertThat(에러코드(() -> festivalAdminService.create(요청)))
                    .isEqualTo(CommonErrorCode.INVALID_DATE_RANGE);
        }

        @Test
        void 주최를_지정하지_않으면_거부한다() {
            FestivalCreateRequest 요청 = 등록요청(null, 임포트_키, "세종연회", 시작일, 종료일);

            assertThat(에러코드(() -> festivalAdminService.create(요청)))
                    .isEqualTo(FestivalErrorCode.FESTIVAL_INVALID_HOST_ID);
        }

        @Test
        void 없는_주최를_지정하면_주최_없음이다() {
            FestivalCreateRequest 요청 = 등록요청(999_999L, 임포트_키, "세종연회", 시작일, 종료일);

            assertThat(에러코드(() -> festivalAdminService.create(요청)))
                    .isEqualTo(HostErrorCode.HOST_NOT_FOUND);
        }

        @Test
        void import_key가_겹치면_거부한다() {
            festivalAdminService.create(등록요청(주최.getId(), 임포트_키, "세종연회", 시작일, 종료일));
            반영한다();

            FestivalCreateRequest 같은_키 = 등록요청(주최.getId(), 임포트_키, "다른 축제", 시작일, 종료일);
            assertThat(에러코드(() -> festivalAdminService.create(같은_키)))
                    .isEqualTo(FestivalErrorCode.FESTIVAL_DUPLICATE_IMPORT_KEY);
        }

        @Test
        void import_key는_비워도_저장된다() {
            FestivalResponse response =
                    festivalAdminService.create(등록요청(주최.getId(), "  ", "세종연회", 시작일, 종료일));

            assertThat(response.importKey()).isNull();
        }
    }

    @Nested
    class 수정 {

        @Test
        @DisplayName("전체 교체 - 생략한 필드는 비워진다")
        void 생략한_필드는_비워진다() {
            Long id = 축제를_만든다();

            festivalAdminService.update(id, 최소한만_담은_수정요청("이름만 바꾼다"));
            반영한다();

            Festival saved = festivalRepository.findById(id).orElseThrow();
            assertThat(saved.getName()).isEqualTo("이름만 바꾼다");
            assertThat(saved.getImportKey()).isNull();
            assertThat(saved.getPosterUrl()).isNull();
            assertThat(saved.getDescription()).isNull();
            assertThat(saved.getVenueName()).isNull();
            assertThat(saved.getAddress()).isNull();
            assertThat(saved.getLatitude()).isNull();
            assertThat(saved.getLongitude()).isNull();
            assertThat(saved.getExternalVisitor()).isNull();
            assertThat(saved.getVerification()).isNull();
            assertThat(saved.getTicketType()).isNull();
            assertThat(saved.getTicketOpenAt()).isNull();
            assertThat(saved.getAdmissionNote()).isNull();
            assertThat(saved.getInstagramUrl()).isNull();
        }

        @Test
        void 빈_문자열도_비우기로_읽는다() {
            Long id = 축제를_만든다();

            festivalAdminService.update(id, new FestivalUpdateRequest(
                    주최.getId(), "", "이름", 시작일, 종료일,
                    "", "", "", "", null, null,
                    null, null, null, null, "", ""));
            반영한다();

            Festival saved = festivalRepository.findById(id).orElseThrow();
            assertThat(saved.getImportKey()).isNull();
            assertThat(saved.getPosterUrl()).isNull();
            assertThat(saved.getVenueName()).isNull();
            assertThat(saved.getInstagramUrl()).isNull();
        }

        @Test
        void 보낸_값은_그대로_반영된다() {
            Long id = 축제를_만든다();

            festivalAdminService.update(id, new FestivalUpdateRequest(
                    주최.getId(), 임포트_키, "새 이름", 시작일, 종료일,
                    null, null, "학생회관", null, 37.1, 127.2,
                    ExternalVisitorPolicy.ALLOWED, null, null, null, null, null));
            반영한다();

            Festival saved = festivalRepository.findById(id).orElseThrow();
            assertThat(saved.getName()).isEqualTo("새 이름");
            assertThat(saved.getVenueName()).isEqualTo("학생회관");
            assertThat(saved.getLatitude()).isEqualTo(37.1);
            assertThat(saved.getLongitude()).isEqualTo(127.2);
            assertThat(saved.getExternalVisitor()).isEqualTo(ExternalVisitorPolicy.ALLOWED);
        }

        @Test
        void 없는_축제면_거부한다() {
            assertThat(에러코드(() -> festivalAdminService.update(999_999L, 최소한만_담은_수정요청("이름"))))
                    .isEqualTo(FestivalErrorCode.FESTIVAL_NOT_FOUND);
        }

        @Test
        void 이름이_공백뿐이면_거부한다() {
            Long id = 축제를_만든다();

            assertThat(에러코드(() -> festivalAdminService.update(id, 최소한만_담은_수정요청("   "))))
                    .isEqualTo(FestivalErrorCode.FESTIVAL_INVALID_NAME);
        }

        @Test
        void 기간을_줄여_기존_라인업이_벗어나면_거부한다() {
            Long id = 축제를_만든다();
            라인업을_넣는다(id, 3);

            FestivalUpdateRequest 하루로_줄이기 = 기간만_바꾼_수정요청(시작일, 시작일);

            assertThat(에러코드(() -> festivalAdminService.update(id, 하루로_줄이기)))
                    .isEqualTo(FestivalErrorCode.FESTIVAL_PERIOD_CONFLICTS_LINEUP);
        }

        @Test
        void 라인업이_기간_안에_들어오면_기간을_줄일_수_있다() {
            Long id = 축제를_만든다();
            라인업을_넣는다(id, 1);

            festivalAdminService.update(id, 기간만_바꾼_수정요청(시작일, 시작일));
            반영한다();

            assertThat(festivalRepository.findById(id).orElseThrow().getEndDate()).isEqualTo(시작일);
        }

        @Test
        void 발행된_축제도_수정된다() {
            Long id = 발행된_축제를_만든다();

            festivalAdminService.update(id, 좌표를_담은_수정요청("이름만 바꾼다"));
            반영한다();

            Festival saved = festivalRepository.findById(id).orElseThrow();
            assertThat(saved.getName()).isEqualTo("이름만 바꾼다");
            assertThat(saved.getPublishedAt()).isEqualTo(발행_시각);
        }

        @Test
        void 발행된_축제의_좌표를_비우면_거부한다() {
            Long id = 발행된_축제를_만든다();

            assertThat(에러코드(() -> festivalAdminService.update(id, 최소한만_담은_수정요청("이름만 바꾼다"))))
                    .isEqualTo(FestivalErrorCode.FESTIVAL_PUBLISHED_COORDINATES_REQUIRED);
        }

        @Test
        void 발행된_축제의_좌표를_한쪽만_보내도_거부한다() {
            Long id = 발행된_축제를_만든다();
            FestivalUpdateRequest 위도만 = new FestivalUpdateRequest(
                    주최.getId(), null, "세종연회", 시작일, 종료일,
                    null, null, null, null, 37.5509, null,
                    null, null, null, null, null, null);

            assertThat(에러코드(() -> festivalAdminService.update(id, 위도만)))
                    .isEqualTo(FestivalErrorCode.FESTIVAL_PUBLISHED_COORDINATES_REQUIRED);
        }

        @Test
        void 미발행_축제는_좌표가_없어도_수정된다() {
            Long id = 축제를_만든다();

            festivalAdminService.update(id, 최소한만_담은_수정요청("이름만 바꾼다"));
            반영한다();

            Festival saved = festivalRepository.findById(id).orElseThrow();
            assertThat(saved.getName()).isEqualTo("이름만 바꾼다");
            assertThat(saved.getLatitude()).isNull();
        }

        @Test
        void 자기_import_key는_중복으로_보지_않는다() {
            Long id = 축제를_만든다();

            festivalAdminService.update(id, new FestivalUpdateRequest(
                    주최.getId(), 임포트_키, "세종연회", 시작일, 종료일,
                    null, null, null, null, null, null,
                    null, null, null, null, null, null));
            반영한다();

            assertThat(festivalRepository.findById(id).orElseThrow().getImportKey()).isEqualTo(임포트_키);
        }
    }

    @Nested
    class 삭제 {

        @Test
        void 발행_중이면_거부한다() {
            Long id = 발행된_축제를_만든다();

            assertThat(에러코드(() -> festivalAdminService.delete(id)))
                    .isEqualTo(FestivalErrorCode.FESTIVAL_ALREADY_PUBLISHED);
        }

        @Test
        void 라인업이_남아_있으면_거부한다() {
            Long id = 축제를_만든다();
            라인업을_넣는다(id, 1);

            assertThat(에러코드(() -> festivalAdminService.delete(id)))
                    .isEqualTo(FestivalErrorCode.FESTIVAL_HAS_LINEUPS);
        }

        @Test
        void 미발행이고_라인업이_없으면_삭제된다() {
            Long id = 축제를_만든다();

            festivalAdminService.delete(id);
            반영한다();

            assertThat(festivalRepository.findById(id)).isEmpty();
        }

        @Test
        void 없는_축제면_거부한다() {
            assertThat(에러코드(() -> festivalAdminService.delete(999_999L)))
                    .isEqualTo(FestivalErrorCode.FESTIVAL_NOT_FOUND);
        }
    }

    private FestivalCreateRequest 등록요청(
            Long hostId, String importKey, String name, LocalDate startDate, LocalDate endDate
    ) {
        return new FestivalCreateRequest(
                hostId, importKey, name, startDate, endDate,
                "https://example.com/poster.png", "설명입니다",
                "대운동장", "서울 광진구 능동로 209", 37.5509, 127.0743,
                ExternalVisitorPolicy.CONDITIONAL, VerificationMethod.STUDENT_ID,
                TicketType.FREE, 티켓_오픈, "학생증 지참", "https://instagram.com/sejong");
    }

    private FestivalUpdateRequest 최소한만_담은_수정요청(String name) {
        return new FestivalUpdateRequest(
                주최.getId(), null, name, 시작일, 종료일,
                null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private FestivalUpdateRequest 좌표를_담은_수정요청(String name) {
        return new FestivalUpdateRequest(
                주최.getId(), null, name, 시작일, 종료일,
                null, null, null, null, 37.5509, 127.0743,
                null, null, null, null, null, null);
    }

    private FestivalUpdateRequest 기간만_바꾼_수정요청(LocalDate startDate, LocalDate endDate) {
        return new FestivalUpdateRequest(
                주최.getId(), null, "세종연회", startDate, endDate,
                null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private Long 축제를_만든다() {
        Long id = festivalAdminService.create(등록요청(주최.getId(), 임포트_키, "세종연회", 시작일, 종료일)).festivalId();
        반영한다();
        return id;
    }

    private Long 발행된_축제를_만든다() {
        Long id = 축제를_만든다();
        festivalRepository.findById(id).orElseThrow().publish(발행_시각);
        반영한다();
        return id;
    }

    private void 라인업을_넣는다(Long festivalId, int day) {
        Festival festival = festivalRepository.findById(festivalId).orElseThrow();
        lineupRepository.save(Lineup.builder().festival(festival).artist(null)
                .day(day).displayOrder(1).build());
        반영한다();
    }

    private ErrorCode 에러코드(Runnable 실행) {
        return catchThrowableOfType(실행::run, FestaException.class).getErrorCode();
    }

    private void 반영한다() {
        em.flush();
        em.clear();
    }
}
