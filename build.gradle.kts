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
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.apache.commons:commons-csv:1.14.1")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // API 문서 자동 생성. Boot BOM이 관리하지 않는 의존성이라 버전을 직접 적는다.
    // 2.x는 Boot 3 전용이고, 3.1.0이 Boot 4.1.0을 대상으로 올라온 버전이다.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    // flyway-core는 아래 둘이 각각 끌고 온다. 따로 적으면 BOM이 정하는 버전을
    // 두 곳에서 관리하는 모양이 되므로 선언하지 않는다.
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // 실제 Postgres로 돌린다. Flyway 마이그레이션과 ddl-auto: validate가
    // 함께 돌아야 스키마와 엔티티가 어긋난 것을 CI가 잡는다.
    // Boot 4에서 테스트 슬라이스가 별도 모듈로 분리됐다 (@DataJpaTest, @AutoConfigureTestDatabase)
    testImplementation("org.springframework.boot:spring-boot-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-jdbc-test")

    // 컨테이너는 PostgresTestSupport가 static 블록에서 직접 띄운다. @Testcontainers
    // (junit-jupiter)도 @ServiceConnection(spring-boot-testcontainers)도 쓰지 않으므로
    // 두 모듈은 넣지 않는다. 코어 testcontainers는 아래 한 줄이 끌고 온다.
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
