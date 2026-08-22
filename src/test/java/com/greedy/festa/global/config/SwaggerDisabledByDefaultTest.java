package com.greedy.festa.global.config;

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

/**
 * 프로파일을 지정하지 않은 상태 — 운영 배포가 이 상태다.
 * 문서를 끄는 것이 기본값이라, 배포 설정을 손대지 않아도 외부에 노출되지 않는다.
 *
 * 스웨거 도입에서 남긴 유일한 테스트다. 문서가 잘 그려지는지는 springdoc의 몫이라
 * 검사하지 않는다. 여기서 지키는 것은 노출 여부 하나 — 공통 설정에 enabled: true가
 * 잘못 들어가면 아무 에러 없이 관리자 API 명세가 인터넷에 열린다.
 */
@SuppressWarnings("NonAsciiCharacters")
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
class SwaggerDisabledByDefaultTest {

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
    void 프로파일이_없으면_api_문서를_내주지_않는다() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");

        assertThat(response.statusCode()).isNotEqualTo(200);
        assertThat(response.body()).doesNotContain("\"openapi\"");
    }

    @Test
    void 프로파일이_없으면_스웨거_화면을_내주지_않는다() throws Exception {
        HttpResponse<String> response = get("/swagger-ui/index.html");

        assertThat(response.statusCode()).isNotEqualTo(200);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
