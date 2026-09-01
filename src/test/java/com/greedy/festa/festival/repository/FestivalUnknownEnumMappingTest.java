package com.greedy.festa.festival.repository;

import com.greedy.festa.festival.entity.ExternalVisitorPolicy;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.entity.TicketType;
import com.greedy.festa.festival.entity.VerificationMethod;
import com.greedy.festa.support.PostgresTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ExtendWith(OutputCaptureExtension.class)
class FestivalUnknownEnumMappingTest extends PostgresTestSupport {

    @Autowired FestivalRepository festivalRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void SQL로_수정한_미지_문자열을_UNKNOWN으로_흡수하고_WARN을_남긴다(CapturedOutput output) {
        Long festivalId = insertFestival("ALLOWED", "NONE", "FREE");
        jdbcTemplate.update("""
                UPDATE festival
                SET external_visitor = ?, verification = ?, ticket_type = ?
                WHERE id = ?
                """, "OUTSIDER_NEW", "FACE_SCAN", "EARLY_BIRD", festivalId);

        Festival festival = inTransaction(() -> festivalRepository.findById(festivalId).orElseThrow());

        assertThat(festival.getExternalVisitor()).isEqualTo(ExternalVisitorPolicy.UNKNOWN);
        assertThat(festival.getVerification()).isEqualTo(VerificationMethod.UNKNOWN);
        assertThat(festival.getTicketType()).isEqualTo(TicketType.UNKNOWN);
        assertThat(output).contains("WARN", "OUTSIDER_NEW", "FACE_SCAN", "EARLY_BIRD");
    }

    @Test
    void 미지값을_읽은_엔티티의_변경은_거부되어_원본_문자열을_보존한다() {
        Long festivalId = insertFestival("OUTSIDER_NEW", "FACE_SCAN", "EARLY_BIRD");

        assertThatThrownBy(() -> inTransaction(() -> {
            Festival festival = festivalRepository.findById(festivalId).orElseThrow();
            festival.publish(Instant.parse("2026-09-01T00:00:00Z"));
            return null;
        })).hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("UNKNOWN 입장 정책은 저장할 수 없습니다.");

        assertThat(jdbcTemplate.queryForMap("""
                SELECT external_visitor, verification, ticket_type
                FROM festival WHERE id = ?
                """, festivalId)).containsEntry("external_visitor", "OUTSIDER_NEW")
                .containsEntry("verification", "FACE_SCAN")
                .containsEntry("ticket_type", "EARLY_BIRD");
    }

    @Test
    void 정상_열거값은_기존처럼_저장하고_조회한다() {
        AtomicReference<Long> festivalId = new AtomicReference<>();
        inTransaction(() -> {
            Festival saved = festivalRepository.save(Festival.builder()
                    .name("정상 축제")
                    .startDate(LocalDate.of(2026, 9, 1))
                    .endDate(LocalDate.of(2026, 9, 2))
                    .externalVisitor(ExternalVisitorPolicy.CONDITIONAL)
                    .verification(VerificationMethod.PRE_BOOKING)
                    .ticketType(TicketType.PAID)
                    .build());
            festivalRepository.flush();
            festivalId.set(saved.getId());
            return null;
        });

        assertThat(jdbcTemplate.queryForMap("""
                SELECT external_visitor, verification, ticket_type
                FROM festival WHERE id = ?
                """, festivalId.get())).containsEntry("external_visitor", "CONDITIONAL")
                .containsEntry("verification", "PRE_BOOKING")
                .containsEntry("ticket_type", "PAID");

        Festival festival = inTransaction(
                () -> festivalRepository.findById(festivalId.get()).orElseThrow());
        assertThat(festival.getExternalVisitor()).isEqualTo(ExternalVisitorPolicy.CONDITIONAL);
        assertThat(festival.getVerification()).isEqualTo(VerificationMethod.PRE_BOOKING);
        assertThat(festival.getTicketType()).isEqualTo(TicketType.PAID);
    }

    @Test
    void null은_세_열거값_모두_DB_NULL로_저장하고_null로_조회한다() {
        AtomicReference<Long> festivalId = new AtomicReference<>();
        inTransaction(() -> {
            Festival saved = festivalRepository.save(Festival.builder()
                    .name("입장 정책 미정 축제")
                    .startDate(LocalDate.of(2026, 9, 1))
                    .endDate(LocalDate.of(2026, 9, 2))
                    .externalVisitor(null)
                    .verification(null)
                    .ticketType(null)
                    .build());
            festivalRepository.flush();
            festivalId.set(saved.getId());
            return null;
        });

        assertThat(jdbcTemplate.queryForMap("""
                SELECT external_visitor, verification, ticket_type
                FROM festival WHERE id = ?
                """, festivalId.get())).containsEntry("external_visitor", null)
                .containsEntry("verification", null)
                .containsEntry("ticket_type", null);

        Festival festival = inTransaction(
                () -> festivalRepository.findById(festivalId.get()).orElseThrow());
        assertThat(festival.getExternalVisitor()).isNull();
        assertThat(festival.getVerification()).isNull();
        assertThat(festival.getTicketType()).isNull();
    }

    private Long insertFestival(String externalVisitor, String verification, String ticketType) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO festival
                    (name, start_date, end_date, external_visitor, verification, ticket_type,
                     created_at, updated_at)
                VALUES ('미지값 축제', DATE '2026-09-01', DATE '2026-09-02', ?, ?, ?, NOW(), NOW())
                RETURNING id
                """, Long.class, externalVisitor, verification, ticketType);
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }
}
