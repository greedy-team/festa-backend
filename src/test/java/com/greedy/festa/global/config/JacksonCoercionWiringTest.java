package com.greedy.festa.global.config;

import com.greedy.festa.admin.repository.AdminUserRepository;
import com.greedy.festa.artist.dto.ArtistUpdateRequest;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.festival.dto.FestivalUpdateRequest;
import com.greedy.festa.festival.repository.FestivalHashtagRepository;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.host.repository.HostRepository;
import com.greedy.festa.importer.repository.ImportBatchRepository;
import com.greedy.festa.importer.repository.ImportCommitRowRepository;
import com.greedy.festa.lineup.repository.LineupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret",
        "app.jwt.secret=test-jwt-secret",
        "app.jwt.admin-secret=ZmVzdGEtYWRtaW4tand0LXRlc3Qtc2VjcmV0LWtleS0zMg==",
        "app.crypto.aes-key=test-aes-key"
})
@DisplayName("전체 교체 계약 - 실제 컨텍스트의 매퍼가 실제 요청 DTO에서도 빈 문자열을 비우기로 읽는다")
@SuppressWarnings("NonAsciiCharacters")
class JacksonCoercionWiringTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 아티스트_수정의_genre_빈_문자열은_null이_된다() {
        ArtistUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"잔나비\",\"genre\":\"\"}", ArtistUpdateRequest.class);

        assertThat(request.genre()).isNull();
    }

    @Test
    void 축제_수정의_enum_세_개는_모두_빈_문자열을_null로_받는다() {
        FestivalUpdateRequest request = objectMapper.readValue("""
                {"name":"대동제","externalVisitor":"","verification":"","ticketType":""}
                """, FestivalUpdateRequest.class);

        assertSoftly(softly -> {
            softly.assertThat(request.externalVisitor()).isNull();
            softly.assertThat(request.verification()).isNull();
            softly.assertThat(request.ticketType()).isNull();
        });
    }

    @Test
    void 축제_수정의_날짜_시각_숫자도_빈_문자열을_null로_받는다() {
        FestivalUpdateRequest request = objectMapper.readValue("""
                {"name":"대동제","startDate":"","ticketOpenAt":"","latitude":""}
                """, FestivalUpdateRequest.class);

        assertSoftly(softly -> {
            softly.assertThat(request.startDate()).isNull();
            softly.assertThat(request.ticketOpenAt()).isNull();
            softly.assertThat(request.latitude()).isNull();
        });
    }

    @Test
    void 문자열_필드의_빈_문자열은_그대로_도착한다() {
        FestivalUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"대동제\",\"venueName\":\"\"}", FestivalUpdateRequest.class);

        assertThat(request.venueName()).isEmpty();
    }

    @MockitoBean
    private HostRepository hostRepository;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @MockitoBean
    private ArtistRepository artistRepository;

    @MockitoBean
    private ArtistAliasRepository artistAliasRepository;

    @MockitoBean
    private LineupRepository lineupRepository;

    @MockitoBean
    private FestivalRepository festivalRepository;

    @MockitoBean
    private ImportBatchRepository importBatchRepository;

    @MockitoBean
    private ImportCommitRowRepository importCommitRowRepository;

    @MockitoBean
    private FestivalHashtagRepository festivalHashtagRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;
}
