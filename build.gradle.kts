import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    id("org.springframework.boot") version "4.1.0"
}

group = "com.greedy"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val jjwtVersion = "0.13.0"
val springdocVersion = "3.1.0"

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.apache.commons:commons-csv:1.14.1")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // 메트릭을 Grafana Cloud로 직접 push한다 (DEC-0182). 새 컨테이너 없이 앱이 스스로 보낸다.
    implementation("io.micrometer:micrometer-registry-otlp")
    // starter가 아니라 모듈이다. 레지스트리만 넣으면 Boot 4.1에서 조용히 아무것도 하지 않는다 —
    // OtlpMetricsExportAutoConfiguration이 OpenTelemetryProperties 클래스를 조건으로 요구한다.
    // starter를 쓰면 tracing·OkHttp·kotlin-stdlib까지 딸려 와 기동 비용이 는다 (DEC-0163 예산).
    implementation("org.springframework.boot:spring-boot-opentelemetry")

    // API 문서 자동 생성. Boot BOM이 관리하지 않는 의존성이라 버전을 직접 적는다.
    // 2.x는 Boot 3 전용이고, 3.1.0이 Boot 4.1.0을 대상으로 올라온 버전이다.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")

    // 테스트 수신기가 OTLP 본문을 디코딩하는 데 쓴다. micrometer-registry-otlp가 의존하는
    // proto 버전과 같다. Boot BOM이 관리하지 않으므로 버전을 적고, Boot를 올릴 때 함께 맞춘다.
    testImplementation("io.opentelemetry.proto:opentelemetry-proto:1.10.0-alpha")

    // 실제 Postgres로 돌린다. Flyway 마이그레이션과 ddl-auto: validate가
    // 함께 돌아야 스키마와 엔티티가 어긋난 것을 CI가 잡는다.
    // Boot 4에서 테스트 슬라이스가 별도 모듈로 분리됐다 (@DataJpaTest, @AutoConfigureTestDatabase)
    testImplementation("org.springframework.boot:spring-boot-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-jdbc-test")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
