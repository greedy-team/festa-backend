package com.greedy.festa.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.annotation.DirtiesContext;
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
import com.sun.net.httpserver.HttpServer;

import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;

/**
 * 앱이 실제로 무엇을 보내는지 본다. 목을 쓰지 않고 JDK HttpServer로 받아 gzip을 풀고
 * OTLP proto로 디코딩한다. 여기서 고정하는 것은 DEC-0184의 이름·라벨 규칙이고,
 * 깨지면 알림 규칙과 대시보드 쿼리가 조용히 빈 화면이 된다.
 */
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
        "app.crypto.aes-key=test-aes-key",
        // 이 테스트에서만 전송을 켠다. 다른 컨텍스트는 application.yml의 기본 꺼짐 그대로다.
        "management.otlp.metrics.export.enabled=true",
        // 포트는 @BeforeAll이 System property로 넘긴다.
        "management.otlp.metrics.export.url=http://localhost:${test.otlp.port}/v1/metrics",
        // yml의 ${GRAFANA_OTLP_AUTH:} 자리에 들어간다. base64에 흔한 +, /, = 를 일부러 담았다.
        "GRAFANA_OTLP_AUTH=a+b/c=="
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OtlpMetricsExportTest {

    private static final LinkedBlockingQueue<RecordedRequest> RECEIVED = new LinkedBlockingQueue<>();

    private static HttpServer receiver;

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

    @BeforeAll
    static void startReceiver() throws Exception {
        receiver = HttpServer.create(new InetSocketAddress(0), 0);
        receiver.createContext("/v1/metrics", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] body = exchange.getRequestBody().readAllBytes();
            RECEIVED.add(new RecordedRequest(authorization, body));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        receiver.start();
        System.setProperty("test.otlp.port", String.valueOf(receiver.getAddress().getPort()));
    }

    @AfterAll
    static void stopReceiver() {
        if (receiver != null) {
            receiver.stop(0);
        }
        System.clearProperty("test.otlp.port");
        RECEIVED.clear();
    }

    @Test
    void exportsExpectedMetricsShape() throws Exception {
        // 매핑 없는 경로를 한 번 호출해 http.server.requests를 만든다.
        httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/no-such-path")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        // close()가 동기 최종 publish를 일으킨다. step(60s)을 기다리지 않는다.
        applicationContext.getBean(OtlpMeterRegistry.class).close();

        RecordedRequest last = lastRequest();
        ExportMetricsServiceRequest request = decode(last.body());

        assertThat(last.authorization()).isEqualTo("Basic a+b/c==");

        ResourceMetrics resourceMetrics = request.getResourceMetrics(0);
        List<KeyValue> attributes = resourceMetrics.getResource().getAttributesList();
        assertThat(attributeValue(attributes, "service.name")).isEqualTo("festa");
        assertThat(attributeValue(attributes, "deployment.environment.name")).isNotBlank();
        assertThat(attributeValue(attributes, "service.instance.id")).isNull();
        assertThat(attributeValue(attributes, "service.version")).isNull();

        List<Metric> metrics = allMetrics(request);

        assertThat(metricNamed(metrics, "process.uptime").getUnit()).isEqualTo("seconds");

        assertThat(metrics).noneMatch(metric -> metric.getName().startsWith("spring.security"));

        // 관측이 만드는 LongTaskTimer를 껐다는 확인이다. executor.active는 gauge라 대상이 아니다.
        assertThat(metrics).noneMatch(metric -> metric.getName().endsWith(".active") && metric.hasHistogram());

        Metric requests = metricNamed(metrics, "http.server.requests");
        assertThat(requests.hasHistogram()).isTrue();
        assertThat(requests.getHistogram().getDataPointsList())
                .allSatisfy(point -> assertThat(point.getExplicitBoundsCount()).isZero());
    }

    private RecordedRequest lastRequest() throws InterruptedException {
        List<RecordedRequest> drained = new ArrayList<>();
        RECEIVED.drainTo(drained);
        if (drained.isEmpty()) {
            RecordedRequest polled = RECEIVED.poll(10, TimeUnit.SECONDS);
            assertThat(polled).as("수신기가 10초 안에 아무 요청도 받지 못했다").isNotNull();
            return polled;
        }
        return drained.get(drained.size() - 1);
    }

    private ExportMetricsServiceRequest decode(byte[] gzipped) throws Exception {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(gzipped))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return ExportMetricsServiceRequest.parseFrom(out.toByteArray());
        }
    }

    private List<Metric> allMetrics(ExportMetricsServiceRequest request) {
        List<Metric> metrics = new ArrayList<>();
        for (ResourceMetrics resourceMetrics : request.getResourceMetricsList()) {
            for (ScopeMetrics scopeMetrics : resourceMetrics.getScopeMetricsList()) {
                metrics.addAll(scopeMetrics.getMetricsList());
            }
        }
        return metrics;
    }

    private Metric metricNamed(List<Metric> metrics, String name) {
        return metrics.stream()
                .filter(metric -> metric.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("메트릭 " + name + "이 전송에 없다. 보낸 이름: "
                        + metrics.stream().map(Metric::getName).sorted().toList()));
    }

    private String attributeValue(List<KeyValue> attributes, String key) {
        for (KeyValue attribute : attributes) {
            if (attribute.getKey().equals(key)) {
                return attribute.getValue().getStringValue();
            }
        }
        return null;
    }

    private record RecordedRequest(String authorization, byte[] body) {
    }
}
