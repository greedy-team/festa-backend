package com.greedy.festa.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class PostgresTestSupport {

    // Optional disposable local database for environments without Docker.
    private static final String LOCAL_URL = System.getenv("FESTA_TEST_POSTGRES_URL");
    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (LOCAL_URL == null || LOCAL_URL.isBlank()) {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
    }

    @DynamicPropertySource
    static void 데이터소스(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> LOCAL_URL);
            registry.add("spring.datasource.username", () -> System.getenv("FESTA_TEST_POSTGRES_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("FESTA_TEST_POSTGRES_PASSWORD"));
        }
    }
}
