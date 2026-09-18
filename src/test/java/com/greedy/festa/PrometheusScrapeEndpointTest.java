package com.greedy.festa;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.greedy.festa.admin.repository.AdminUserRepository;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.festival.repository.FestivalHashtagRepository;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.host.repository.HostRepository;
import com.greedy.festa.importer.repository.ImportBatchRepository;
import com.greedy.festa.importer.repository.ImportCommitRowRepository;
import com.greedy.festa.lineup.repository.LineupRepository;

/**
 * A1의 Prometheus가 스크랩할 엔드포인트를 실물로 띄워 계약으로 고정한다 (DOC-0018 3단계).
 *
 * 앱은 이 엔드포인트를 인증 없이 서빙한다 — SecurityConfig가 permitAll이기 때문이다.
 * 인터넷에 대한 경계는 앱이 아니라 Caddy가 404로 친다 (DEC-0214). 그쪽은
 * deploy/test_caddyfile.py가 실물 caddy 컨테이너로 따로 검증한다.
 * Prometheus는 Caddy를 거치지 않고 compose 네트워크로 app-<색>:8080에 직접 닿는다.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "app.jwt.secret=test-jwt-secret",
        "app.jwt.admin-secret=ZmVzdGEtYWRtaW4tand0LXRlc3Qtc2VjcmV0LWtleS0zMg==",
        "app.crypto.aes-key=test-aes-key"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrometheusScrapeEndpointTest {

    /** 대시보드와 알림이 이 이름으로 조회한다. 이름이 바뀌면 화면이 조용히 빈다. */
    private static final List<String> REQUIRED_METRICS = List.of(
            "jvm_memory_used_bytes",
            "jvm_gc_pause_seconds",
            "jvm_threads_live_threads",
            "process_cpu_usage",
            "system_cpu_count",
            "application_ready_time_seconds");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @MockitoBean private HostRepository hostRepository;
    @MockitoBean private AdminUserRepository adminUserRepository;
    @MockitoBean private ArtistRepository artistRepository;
    @MockitoBean private ArtistAliasRepository artistAliasRepository;
    @MockitoBean private LineupRepository lineupRepository;
    @MockitoBean private FestivalRepository festivalRepository;
    @MockitoBean private ImportBatchRepository importBatchRepository;
    @MockitoBean private ImportCommitRowRepository importCommitRowRepository;
    @MockitoBean private FestivalHashtagRepository festivalHashtagRepository;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Value("${local.server.port}")
    private int port;

    @Test
    void scrapeEndpointIsExposed() throws Exception {
        HttpResponse<String> response = get("/actuator/prometheus");

        assertThat(response.statusCode())
                .describedAs("노출 목록에 prometheus가 빠지면 404가 된다 (application.yml)")
                .isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse(""))
                .startsWith("text/plain");
    }

    @Test
    void scrapeCarriesTheMetricsDashboardsQuery() throws Exception {
        String body = get("/actuator/prometheus").body();

        assertThat(REQUIRED_METRICS).allSatisfy(metric ->
                assertThat(body)
                        .describedAs("%s 가 사라졌다 — 이 이름을 쓰는 패널이 빈다", metric)
                        .contains("# TYPE " + metric));
    }

    @Test
    void requestTimingsCarryTheUriLabel() throws Exception {
        get("/actuator/health");

        String body = get("/actuator/prometheus").body();

        // 「어느 API가 느린가」에 답하는 유일한 근거다. uri 라벨이 빠지면 전부 한 덩어리가 된다.
        assertThat(body).contains("http_server_requests_seconds_count");
        assertThat(body).contains("uri=\"/actuator/health\"");
    }

    @Test
    void timersStayAsSummariesSoSeriesDoNotExplode() throws Exception {
        String body = get("/actuator/prometheus").body();

        // 빈 응답에 속지 않도록 먼저 실제로 스크랩이 왔는지부터 본다.
        // 이게 없으면 엔드포인트가 404여도 이 테스트는 조용히 통과한다.
        List<String> typeLines = body.lines().filter(line -> line.startsWith("# TYPE")).toList();
        assertThat(typeLines).hasSizeGreaterThan(20);

        // DEC-0184: percentiles-histogram 금지 — 시리즈 예산 10k를 지키는 불변식이다.
        // 지금은 Micrometer 기본값이라 저절로 지켜지지만, 누가 켜면 타이머마다 버킷이 붙는다.
        assertThat(typeLines).noneMatch(line -> line.endsWith(" histogram"));
        assertThat(body).doesNotContain("_bucket{");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
