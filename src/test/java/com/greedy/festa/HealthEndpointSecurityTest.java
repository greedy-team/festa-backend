package com.greedy.festa;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
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

import io.micrometer.registry.otlp.OtlpMeterRegistry;

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
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointSecurityTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

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

    @Autowired
    private ApplicationContext applicationContext;

    @Value("${local.server.port}")
    private int port;

    // 로컬·CI·테스트가 아무것도 내보내지 않는다는 보장이다 (DEC-0185).
    // 모듈이 클래스패스에 들어오면 export 기본값이 켜짐이라, application.yml에서 끄지 않으면
    // 모든 테스트 컨텍스트가 localhost:4318로 매분 push를 시도한다.
    @Test
    void otlpRegistryIsAbsentByDefault() {
        OtlpMeterRegistry registry = applicationContext.getBeanProvider(OtlpMeterRegistry.class).getIfAvailable();

        assertThat(registry).isNull();
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
