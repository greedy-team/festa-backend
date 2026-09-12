package com.greedy.festa.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.yaml.snakeyaml.Yaml;

/**
 * compose가 주는 환경변수 + application.yml을 Boot의 실제 바인딩에 태워 읽는다.
 * 테스트는 이런 값을 인라인으로 주입하므로 배선이 빠져도 CI는 초록이다 (이슈 #47).
 * 이 테스트만이 그 틈을 본다.
 */
class TelemetryWiringContractTest {

    private static final Path COMPOSE = Path.of("deploy/compose.yaml");
    private static final Path APPLICATION_YML = Path.of("src/main/resources/application.yml");
    private static final Path CD_WORKFLOW = Path.of(".github/workflows/PROJECT-SPRING-CD.yaml");
    private static final String TEST_AUTH = "a+b/c==";

    @Test
    void composeEnvironmentBindsToOtlpExportProperties() throws IOException {
        StandardEnvironment environment = environmentFromComposeAndYaml();
        Binder binder = Binder.get(environment);

        assertThat(binder.bind("management.otlp.metrics.export.enabled", Boolean.class).orElse(false)).isTrue();

        String url = binder.bind("management.otlp.metrics.export.url", String.class).orElse("");
        URI uri = URI.create(url);
        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getPath()).isEqualTo("/otlp/v1/metrics");
        assertThat(uri.getHost())
                .as("자리표시자를 남긴 채 커밋하지 않는다")
                .doesNotContain("<")
                .endsWith(".grafana.net");

        Map<String, String> headers = binder
                .bind("management.otlp.metrics.export.headers", Bindable.mapOf(String.class, String.class))
                .orElse(Map.of());
        assertThat(headers).containsEntry("Authorization", "Basic " + TEST_AUTH);
    }

    @Test
    void deployWorkflowPassesTheSecretThrough() throws IOException {
        String workflow = Files.readString(CD_WORKFLOW);

        assertThat(workflow).contains("GRAFANA_OTLP_AUTH: ${{ secrets.GRAFANA_OTLP_AUTH }}");
        assertThat(workflow).contains("write_env GRAFANA_OTLP_AUTH \"$GRAFANA_OTLP_AUTH\"");
    }

    private StandardEnvironment environmentFromComposeAndYaml() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        composeEnvironment()));

        List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                .load("application.yml", new FileSystemResource(APPLICATION_YML));
        loaded.forEach(environment.getPropertySources()::addLast);

        ConfigurationPropertySources.attach(environment);
        return environment;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> composeEnvironment() throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(COMPOSE));
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        Map<String, Object> app = (Map<String, Object>) services.get("app");
        Map<String, Object> declared = (Map<String, Object>) app.get("environment");

        Map<String, Object> resolved = new LinkedHashMap<>();
        declared.forEach((key, value) -> {
            if ("GRAFANA_OTLP_AUTH".equals(key)) {
                resolved.put(key, TEST_AUTH);
                return;
            }
            resolved.put(key, value);
        });
        return resolved;
    }
}
