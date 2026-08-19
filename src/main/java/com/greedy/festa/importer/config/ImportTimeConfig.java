package com.greedy.festa.importer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ImportTimeConfig {
    @Bean
    Clock importClock() {
        return Clock.systemUTC();
    }
}
