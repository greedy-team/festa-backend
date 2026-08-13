package com.greedy.festa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

@Configuration
public class SecurityConfig {

    @Bean
    WebSecurityCustomizer healthEndpointSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers("/actuator/health", "/actuator/health/**");
    }
}
