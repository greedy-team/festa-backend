package com.greedy.festa.global.logging;

import ch.qos.logback.classic.Level;
import com.greedy.festa.artist.dto.ArtistMergeRequest;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.artist.service.ArtistAdminService;
import com.greedy.festa.artist.service.ArtistMergeService;
import com.greedy.festa.festival.dto.FestivalPublishFailureReason;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.festival.service.FestivalAdminService;
import com.greedy.festa.festival.service.FestivalPublishService;
import com.greedy.festa.global.security.JwtAuthenticationFilter;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.repository.HostRepository;
import com.greedy.festa.host.service.HostAdminService;
import com.greedy.festa.lineup.entity.Lineup;
import com.greedy.festa.lineup.repository.LineupRepository;
import com.greedy.festa.lineup.service.LineupAdminService;
import com.greedy.festa.support.LogCaptor;
import com.greedy.festa.support.PostgresTestSupport;
import com.greedy.festa.support.fixture.ArtistFixture;
import com.greedy.festa.support.fixture.FestivalFixture;
import com.greedy.festa.support.fixture.HostFixture;
import com.greedy.festa.support.fixture.LineupFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리 작업 로그는 트랜잭션이 실제로 커밋된 뒤에만 남아야 한다.
 *
 * <p>커밋 전에 찍으면 뒤이어 롤백된 작업이 "수행됨"으로 기록돼, 되짚기의 근거여야 할
 * 로그가 하지 않은 일을 했다고 말한다. 이 클래스는 그 성질을 두 축으로 잠근다 —
 * 메커니즘 자체(커밋·트랜잭션 밖·MDC)와, 로그를 남기는 서비스 7곳 각각의 롤백 동작.
 */
@SuppressWarnings("NonAsciiCharacters")
@SpringBootTest
@ActiveProfiles("test")
class AfterCommitLogTest extends PostgresTestSupport {

    private static final String 접두어 = "로그검증-";

    @Autowired
    private HostAdminService hostAdminService;

    @Autowired
    private ArtistAdminService artistAdminService;

    @Autowired
    private ArtistMergeService artistMergeService;

    @Autowired
    private FestivalAdminService festivalAdminService;

    @Autowired
    private FestivalPublishService festivalPublishService;

    @Autowired
    private LineupAdminService lineupAdminService;

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private FestivalRepository festivalRepository;

    @Autowired
    private LineupRepository lineupRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private Host 주최;

    @BeforeEach
    void 기본_주최를_둔다() {
        주최 = 주최를_둔다("주최");
    }

    /**
     * 이 클래스는 실제로 커밋한다 — 그게 검증 대상이다. 그래서 만든 행이 컨테이너에 남으므로,
     * 시드와 다른 테스트의 행은 건드리지 않도록 접두어가 붙은 것만 FK 순서대로 지운다.
     */
    @AfterEach
    void 만든_행을_지운다() {
        jdbc.update("DELETE FROM lineup WHERE festival_id IN (SELECT id FROM festival WHERE name LIKE ?)",
                접두어 + "%");
        jdbc.update("DELETE FROM lineup WHERE artist_id IN (SELECT id FROM artist WHERE name LIKE ?)",
                접두어 + "%");
        jdbc.update("DELETE FROM festival WHERE name LIKE ?", 접두어 + "%");
        jdbc.update("DELETE FROM artist_alias WHERE artist_id IN (SELECT id FROM artist WHERE name LIKE ?)",
                접두어 + "%");
        jdbc.update("DELETE FROM artist_alias WHERE name LIKE ?", 접두어 + "%");
        jdbc.update("DELETE FROM artist WHERE name LIKE ?", 접두어 + "%");
        jdbc.update("DELETE FROM host WHERE name LIKE ?", 접두어 + "%");
    }

    // ---------- 메커니즘 ----------

    @Test
    void 커밋되면_작업_로그가_남는다() {
        // given
        Host 지울_주최 = 주최를_둔다("지울주최");

        // when — 서비스 자체 트랜잭션이 커밋된다
        List<String> 남은_로그 = 로그를_받는다(HostAdminService.class,
                () -> hostAdminService.delete(지울_주최.getId()));

        // then — 커밋을 기다리느라 로그를 통째로 삼키면 안 된다
        assertThat(hostRepository.findById(지울_주최.getId())).isEmpty();
        assertThat(남은_로그).anySatisfy(줄 -> {
            assertThat(줄).contains("주최 삭제");
            assertThat(줄).contains(String.valueOf(지울_주최.getId()));
        });
    }

    @Test
    void 트랜잭션_밖에서_부르면_그_자리에서_남는다() {
        // when — 기다릴 커밋이 없다
        List<String> 남은_로그 = 로그를_받는다(AfterCommitLogTest.class,
                () -> AfterCommitLogger.info(LoggerFactory.getLogger(AfterCommitLogTest.class),
                        "트랜잭션 밖 - value={}", 7));

        // then — 조용히 버리면 로그가 통째로 사라진다
        assertThat(남은_로그).containsExactly("트랜잭션 밖 - value=7");
    }

    @Test
    void 커밋_뒤에_남는_로그도_관리자_식별자를_달고_있다() {
        // given
        Host 지울_주최 = 주최를_둔다("지울주최");

        // when — 필터가 넣는 MDC를 흉내낸다
        List<String> 남은_관리자;
        MDC.put(JwtAuthenticationFilter.ADMIN_MDC_KEY, "festa-admin");
        try (LogCaptor 로그 = LogCaptor.forClass(HostAdminService.class)) {
            hostAdminService.delete(지울_주최.getId());
            남은_관리자 = 로그.mdcValues(JwtAuthenticationFilter.ADMIN_MDC_KEY);
        } finally {
            MDC.remove(JwtAuthenticationFilter.ADMIN_MDC_KEY);
        }

        // then — 콜백이 요청 스레드를 벗어나면 여기서 깨진다. #117의 관리자 식별자가 사라지는 자리다
        assertThat(남은_관리자).containsExactly("festa-admin");
    }

    @Test
    void 커밋되면_발행_취소_로그가_남는다() {
        // given
        Festival 축제 = 발행한_축제("발행된축제");

        // when
        List<String> 남은_로그 = 로그를_받는다(FestivalPublishService.class,
                () -> festivalPublishService.unpublish(축제.getId()));

        // then
        assertThat(festivalRepository.findById(축제.getId()).orElseThrow().getPublishedAt()).isNull();
        assertThat(남은_로그).anySatisfy(줄 -> {
            assertThat(줄).contains("발행 취소");
            assertThat(줄).contains(String.valueOf(축제.getId()));
        });
    }

    @Test
    void 커밋되면_일괄_발행_요약이_실패까지_담아_남는다() {
        // given — 응답은 publishedIds와 failed 둘로 고정돼 건수를 담지 않으므로 요약은 로그에만 있다
        Festival 온전한_축제 = 발행_가능한_축제("온전한축제");
        Festival 라인업이_없는_축제 = 축제를_둔다("라인업없는축제");

        // when
        List<String> 남은_로그 = 로그를_받는다(FestivalPublishService.class,
                () -> festivalPublishService.batchPublish(
                        List.of(온전한_축제.getId(), 라인업이_없는_축제.getId())));

        // then
        assertThat(남은_로그).anySatisfy(줄 -> {
            assertThat(줄).contains("요청 2건");
            assertThat(줄).contains("발행 1건");
            assertThat(줄).contains("실패 1건");
            assertThat(줄).contains(String.valueOf(라인업이_없는_축제.getId()));
            assertThat(줄).contains(FestivalPublishFailureReason.LINEUP_EMPTY.name());
        });
    }

    // ---------- 로그를 남기는 7곳 ----------
    // 각 테스트의 첫 단언이 "롤백이 실제로 일어났다"를 확인한다. 그게 없으면 로그가 없는 이유가
    // 커밋 대기 때문인지 작업이 아예 안 돌았기 때문인지 갈리지 않는다.

    @Test
    void 롤백되면_주최_삭제_로그가_남지_않는다() {
        Host 지울_주최 = 주최를_둔다("지울주최");

        List<String> 남은_로그 = 롤백하며_로그를_받는다(HostAdminService.class,
                () -> hostAdminService.delete(지울_주최.getId()));

        assertThat(hostRepository.findById(지울_주최.getId())).isPresent();
        assertThat(남은_로그).noneSatisfy(줄 -> assertThat(줄).contains("주최 삭제"));
    }

    @Test
    void 롤백되면_아티스트_삭제_로그가_남지_않는다() {
        Artist 아티스트 = 아티스트를_둔다("지울아티스트");

        List<String> 남은_로그 = 롤백하며_로그를_받는다(ArtistAdminService.class,
                () -> artistAdminService.delete(아티스트.getId()));

        assertThat(artistRepository.findById(아티스트.getId())).isPresent();
        assertThat(남은_로그).noneSatisfy(줄 -> assertThat(줄).contains("아티스트 삭제"));
    }

    @Test
    void 롤백되면_아티스트_병합_로그가_남지_않는다() {
        Artist 남길_아티스트 = 아티스트를_둔다("남길아티스트");
        Artist 흡수될_아티스트 = 아티스트를_둔다("흡수될아티스트");

        List<String> 남은_로그 = 롤백하며_로그를_받는다(ArtistMergeService.class,
                () -> artistMergeService.merge(new ArtistMergeRequest(
                        남길_아티스트.getId(), List.of(흡수될_아티스트.getId()), true)));

        assertThat(artistRepository.findById(흡수될_아티스트.getId())).isPresent();
        assertThat(남은_로그).noneSatisfy(줄 -> assertThat(줄).contains("아티스트 병합"));
    }

    @Test
    void 롤백되면_축제_삭제_로그가_남지_않는다() {
        Festival 축제 = 축제를_둔다("지울축제");

        List<String> 남은_로그 = 롤백하며_로그를_받는다(FestivalAdminService.class,
                () -> festivalAdminService.delete(축제.getId()));

        assertThat(festivalRepository.findById(축제.getId())).isPresent();
        assertThat(남은_로그).noneSatisfy(줄 -> assertThat(줄).contains("축제 삭제"));
    }

    @Test
    void 롤백되면_라인업_삭제_로그가_남지_않는다() {
        Festival 축제 = 축제를_둔다("라인업있는축제");
        Lineup 라인업 = 라인업을_올린다(축제, 아티스트를_둔다("출연아티스트"));

        List<String> 남은_로그 = 롤백하며_로그를_받는다(LineupAdminService.class,
                () -> lineupAdminService.delete(축제.getId(), 라인업.getId()));

        assertThat(lineupRepository.findById(라인업.getId())).isPresent();
        assertThat(남은_로그).noneSatisfy(줄 -> assertThat(줄).contains("라인업 삭제"));
    }

    @Test
    void 롤백되면_발행_취소_로그가_남지_않는다() {
        Festival 축제 = 발행한_축제("발행된축제");

        List<String> 남은_로그 = 롤백하며_로그를_받는다(FestivalPublishService.class,
                () -> festivalPublishService.unpublish(축제.getId()));

        assertThat(festivalRepository.findById(축제.getId()).orElseThrow().getPublishedAt()).isNotNull();
        assertThat(남은_로그).noneSatisfy(줄 -> assertThat(줄).contains("발행 취소"));
    }

    @Test
    void 롤백되면_일괄_발행_요약이_남지_않는다() {
        Festival 축제 = 발행_가능한_축제("일괄발행축제");

        List<String> 남은_로그 = 롤백하며_로그를_받는다(FestivalPublishService.class,
                () -> festivalPublishService.batchPublish(List.of(축제.getId())));

        assertThat(festivalRepository.findById(축제.getId()).orElseThrow().getPublishedAt()).isNull();
        assertThat(남은_로그).noneSatisfy(줄 -> assertThat(줄).contains("일괄 발행"));
    }

    // ---------- 거들기 ----------

    private List<String> 로그를_받는다(Class<?> 대상, Runnable 작업) {
        try (LogCaptor 로그 = LogCaptor.forClass(대상)) {
            작업.run();
            return 로그.messagesAt(Level.INFO);
        }
    }

    private List<String> 롤백하며_로그를_받는다(Class<?> 대상, Runnable 작업) {
        return 로그를_받는다(대상, () -> new TransactionTemplate(transactionManager).execute(상태 -> {
            작업.run();
            상태.setRollbackOnly();
            return null;
        }));
    }

    private Host 주최를_둔다(String 이름) {
        return hostRepository.save(HostFixture.host(접두어 + 이름).region("서울 광진구").build());
    }

    private Artist 아티스트를_둔다(String 이름) {
        return artistRepository.save(ArtistFixture.artist(접두어 + 이름).build());
    }

    private Festival 축제를_둔다(String 이름) {
        return festivalRepository.save(FestivalFixture.publishable(접두어 + 이름, 주최)
                .startDate(LocalDate.of(2026, 9, 10))
                .endDate(LocalDate.of(2026, 9, 12))
                .longitude(127.0743)
                .build());
    }

    private Lineup 라인업을_올린다(Festival 축제, Artist 아티스트) {
        return lineupRepository.save(LineupFixture.lineup(축제, 아티스트).build());
    }

    private Festival 발행_가능한_축제(String 이름) {
        Festival 축제 = 축제를_둔다(이름);
        라인업을_올린다(축제, 아티스트를_둔다(이름 + "출연"));
        return 축제;
    }

    private Festival 발행한_축제(String 이름) {
        Festival 축제 = 발행_가능한_축제(이름);
        festivalPublishService.publish(축제.getId());
        return 축제;
    }
}
