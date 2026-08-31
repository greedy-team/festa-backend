package com.greedy.festa.lineup.service;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.global.exception.ErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.lineup.dto.LineupCreateRequest;
import com.greedy.festa.lineup.dto.LineupResponse;
import com.greedy.festa.lineup.dto.LineupUpdateRequest;
import com.greedy.festa.lineup.exception.LineupErrorCode;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SuppressWarnings("NonAsciiCharacters")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class LineupAdminServiceTest extends PostgresTestSupport {

    private static final LocalDate 시작일 = LocalDate.of(2026, 5, 20);
    private static final LocalDate 종료일 = LocalDate.of(2026, 5, 22);

    @Autowired
    private FestivalRepository festivalRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private LineupRepository lineupRepository;

    @Autowired
    private EntityManager em;

    private LineupAdminService lineupAdminService;
    private Festival 축제;
    private Artist 아티스트;

    @BeforeEach
    void setUp() {
        lineupAdminService = new LineupAdminService(festivalRepository, artistRepository, lineupRepository);
        Host 주최 = em.merge(Host.builder().name("테스트대학교").region("서울 광진구").build());
        축제 = em.merge(Festival.builder()
                .host(주최).name("세종연회").startDate(시작일).endDate(종료일).build());
        아티스트 = em.merge(Artist.builder()
                .name("테스트밴드").genre(ArtistGenre.BAND).needsReview(false).build());
        반영한다();
    }

    @Nested
    class 등록 {

        @Test
        void 아티스트를_지정하면_그대로_연결된다() {
            LineupResponse response = lineupAdminService.create(
                    축제.getId(), new LineupCreateRequest(아티스트.getId(), 1, 1));
            반영한다();

            assertThat(response.artistId()).isEqualTo(아티스트.getId());
            assertThat(response.festivalId()).isEqualTo(축제.getId());
            assertThat(response.day()).isEqualTo(1);
            assertThat(response.displayOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("artistId를 비우면 시크릿 게스트로 저장된다 - artist_id IS NULL이 시크릿이라는 계약")
        void artistId를_비우면_시크릿_게스트다() {
            LineupResponse response = lineupAdminService.create(
                    축제.getId(), new LineupCreateRequest(null, 1, 1));
            반영한다();

            assertThat(response.artistId()).isNull();
            assertThat(lineupRepository.findById(response.lineupId()).orElseThrow().getArtist()).isNull();
        }

        @Test
        void 없는_축제면_거부한다() {
            LineupCreateRequest 요청 = new LineupCreateRequest(아티스트.getId(), 1, 1);

            assertThat(에러코드(() -> lineupAdminService.create(999_999L, 요청)))
                    .isEqualTo(FestivalErrorCode.FESTIVAL_NOT_FOUND);
        }

        @Test
        void 없는_아티스트면_거부한다() {
            LineupCreateRequest 요청 = new LineupCreateRequest(999_999L, 1, 1);

            assertThat(에러코드(() -> lineupAdminService.create(축제.getId(), 요청)))
                    .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
        }

        @Test
        void 일차가_없으면_거부한다() {
            LineupCreateRequest 요청 = new LineupCreateRequest(아티스트.getId(), null, 1);

            assertThat(에러코드(() -> lineupAdminService.create(축제.getId(), 요청)))
                    .isEqualTo(LineupErrorCode.LINEUP_INVALID_DAY);
        }

        @Test
        void 일차가_1보다_작으면_거부한다() {
            LineupCreateRequest 요청 = new LineupCreateRequest(아티스트.getId(), 0, 1);

            assertThat(에러코드(() -> lineupAdminService.create(축제.getId(), 요청)))
                    .isEqualTo(LineupErrorCode.LINEUP_INVALID_DAY);
        }

        @Test
        @DisplayName("3일 축제의 마지막 날인 3일차는 통과한다 - 상한의 +1 경계")
        void 기간의_마지막_일차는_통과한다() {
            LineupResponse response = lineupAdminService.create(
                    축제.getId(), new LineupCreateRequest(아티스트.getId(), 3, 1));

            assertThat(response.day()).isEqualTo(3);
        }

        @Test
        void 일차가_축제_기간을_넘으면_거부한다() {
            LineupCreateRequest 요청 = new LineupCreateRequest(아티스트.getId(), 4, 1);

            assertThat(에러코드(() -> lineupAdminService.create(축제.getId(), 요청)))
                    .isEqualTo(LineupErrorCode.LINEUP_DAY_OUT_OF_RANGE);
        }

        @Test
        void 무대_순서가_없으면_거부한다() {
            LineupCreateRequest 요청 = new LineupCreateRequest(아티스트.getId(), 1, null);

            assertThat(에러코드(() -> lineupAdminService.create(축제.getId(), 요청)))
                    .isEqualTo(LineupErrorCode.LINEUP_INVALID_DISPLAY_ORDER);
        }

        @Test
        void 같은_일차의_같은_순서면_거부한다() {
            lineupAdminService.create(축제.getId(), new LineupCreateRequest(아티스트.getId(), 1, 1));
            반영한다();

            LineupCreateRequest 같은_자리 = new LineupCreateRequest(null, 1, 1);
            assertThat(에러코드(() -> lineupAdminService.create(축제.getId(), 같은_자리)))
                    .isEqualTo(LineupErrorCode.LINEUP_DUPLICATE_SLOT);
        }

        @Test
        void 일차가_다르면_같은_순서를_쓸_수_있다() {
            lineupAdminService.create(축제.getId(), new LineupCreateRequest(아티스트.getId(), 1, 1));
            반영한다();

            LineupResponse response = lineupAdminService.create(
                    축제.getId(), new LineupCreateRequest(아티스트.getId(), 2, 1));

            assertThat(response.day()).isEqualTo(2);
        }
    }

    @Nested
    class 수정 {

        @Test
        @DisplayName("전체 교체 - artistId를 비우면 시크릿 게스트로 되돌아간다")
        void artistId를_비우면_시크릿으로_되돌아간다() {
            Long lineupId = 라인업을_만든다(아티스트.getId(), 1, 1);

            LineupResponse response = lineupAdminService.update(
                    축제.getId(), lineupId, new LineupUpdateRequest(null, 1, 1));
            반영한다();

            assertThat(response.artistId()).isNull();
            assertThat(lineupRepository.findById(lineupId).orElseThrow().getArtist()).isNull();
        }

        @Test
        void 일차와_순서를_바꾼다() {
            Long lineupId = 라인업을_만든다(아티스트.getId(), 1, 1);

            LineupResponse response = lineupAdminService.update(
                    축제.getId(), lineupId, new LineupUpdateRequest(아티스트.getId(), 2, 3));
            반영한다();

            assertThat(response.day()).isEqualTo(2);
            assertThat(response.displayOrder()).isEqualTo(3);
        }

        @Test
        @DisplayName("바꾸지 않은 자기 자리는 중복으로 보지 않는다")
        void 자기_슬롯은_중복이_아니다() {
            Long lineupId = 라인업을_만든다(아티스트.getId(), 1, 1);

            LineupResponse response = lineupAdminService.update(
                    축제.getId(), lineupId, new LineupUpdateRequest(null, 1, 1));

            assertThat(response.lineupId()).isEqualTo(lineupId);
        }

        @Test
        void 다른_라인업이_쓰는_자리면_거부한다() {
            라인업을_만든다(아티스트.getId(), 1, 1);
            Long 옮길_라인업 = 라인업을_만든다(아티스트.getId(), 1, 2);

            LineupUpdateRequest 요청 = new LineupUpdateRequest(아티스트.getId(), 1, 1);
            assertThat(에러코드(() -> lineupAdminService.update(축제.getId(), 옮길_라인업, 요청)))
                    .isEqualTo(LineupErrorCode.LINEUP_DUPLICATE_SLOT);
        }

        @Test
        void 일차가_축제_기간을_넘으면_거부한다() {
            Long lineupId = 라인업을_만든다(아티스트.getId(), 1, 1);

            LineupUpdateRequest 요청 = new LineupUpdateRequest(아티스트.getId(), 4, 1);
            assertThat(에러코드(() -> lineupAdminService.update(축제.getId(), lineupId, 요청)))
                    .isEqualTo(LineupErrorCode.LINEUP_DAY_OUT_OF_RANGE);
        }

        @Test
        @DisplayName("그 축제의 라인업이 아니면 404 - 경로의 축제와 라인업이 어긋나는 경우")
        void 다른_축제의_라인업이면_거부한다() {
            Long lineupId = 라인업을_만든다(아티스트.getId(), 1, 1);
            Long 다른_축제 = 다른_축제를_만든다();

            LineupUpdateRequest 요청 = new LineupUpdateRequest(아티스트.getId(), 1, 1);
            assertThat(에러코드(() -> lineupAdminService.update(다른_축제, lineupId, 요청)))
                    .isEqualTo(LineupErrorCode.LINEUP_NOT_FOUND);
        }
    }

    @Nested
    class 삭제 {

        @Test
        void 삭제된다() {
            Long lineupId = 라인업을_만든다(아티스트.getId(), 1, 1);

            lineupAdminService.delete(축제.getId(), lineupId);
            반영한다();

            assertThat(lineupRepository.findById(lineupId)).isEmpty();
        }

        @Test
        @DisplayName("그 축제의 라인업이 아니면 404 - 남의 축제 라인업이 지워지지 않는다")
        void 다른_축제의_라인업이면_거부한다() {
            Long lineupId = 라인업을_만든다(아티스트.getId(), 1, 1);
            Long 다른_축제 = 다른_축제를_만든다();

            assertThat(에러코드(() -> lineupAdminService.delete(다른_축제, lineupId)))
                    .isEqualTo(LineupErrorCode.LINEUP_NOT_FOUND);
            assertThat(lineupRepository.findById(lineupId)).isPresent();
        }

        @Test
        void 없는_라인업이면_거부한다() {
            assertThat(에러코드(() -> lineupAdminService.delete(축제.getId(), 999_999L)))
                    .isEqualTo(LineupErrorCode.LINEUP_NOT_FOUND);
        }
    }

    @Nested
    class 단건조회 {

        @Test
        void 축제명과_아티스트명을_함께_돌려준다() {
            Long lineupId = 라인업을_만든다(아티스트.getId(), 1, 1);

            LineupResponse response = lineupAdminService.findOne(축제.getId(), lineupId);

            assertThat(response.festivalName()).isEqualTo(축제.getName());
            assertThat(response.artistName()).isEqualTo(아티스트.getName());
        }

        @Test
        void 다른_축제의_라인업이면_거부한다() {
            Long lineupId = 라인업을_만든다(아티스트.getId(), 1, 1);
            Long 다른_축제 = 다른_축제를_만든다();

            assertThat(에러코드(() -> lineupAdminService.findOne(다른_축제, lineupId)))
                    .isEqualTo(LineupErrorCode.LINEUP_NOT_FOUND);
        }

        @Test
        void 없는_라인업이면_거부한다() {
            assertThat(에러코드(() -> lineupAdminService.findOne(축제.getId(), 999_999L)))
                    .isEqualTo(LineupErrorCode.LINEUP_NOT_FOUND);
        }
    }

    private Long 라인업을_만든다(Long artistId, int day, int displayOrder) {
        Long id = lineupAdminService.create(
                축제.getId(), new LineupCreateRequest(artistId, day, displayOrder)).lineupId();
        반영한다();
        return id;
    }

    private Long 다른_축제를_만든다() {
        Festival 다른 = festivalRepository.save(Festival.builder()
                .host(축제.getHost()).name("다른 축제").startDate(시작일).endDate(종료일).build());
        반영한다();
        return 다른.getId();
    }

    private ErrorCode 에러코드(Runnable 실행) {
        return catchThrowableOfType(실행::run, FestaException.class).getErrorCode();
    }

    private void 반영한다() {
        em.flush();
        em.clear();
    }
}
