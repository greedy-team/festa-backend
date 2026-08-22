package com.greedy.festa;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.greedy.festa.admin.repository.AdminUserRepository;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.artist.repository.LineupRepository;
import com.greedy.festa.host.repository.HostRepository;

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
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Value("${local.server.port}")
    private int port;

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
