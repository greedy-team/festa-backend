package com.greedy.festa.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청마다 번호를 발급해 그 요청에서 나온 로그를 서로 묶고, 요청당 한 줄로 접속 기록을 남긴다.
 * 번호가 로그에 찍히는 것은 application.yml의 logging.pattern.correlation이 맡는다.
 */
@Slf4j
public class AccessLogFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "requestId";

    private static final int REQUEST_ID_LENGTH = 8;
    private static final String HEALTH_PATH = "/actuator/health";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {

        long startedAt = System.nanoTime();
        MDC.put(REQUEST_ID, newRequestId());
        try {
            filterChain.doFilter(request, response);
        } finally {
            writeAccessLog(request, response, startedAt);
            // 스레드가 재사용되므로, 비우지 않으면 다음 요청이 이 번호를 물려받는다.
            MDC.remove(REQUEST_ID);
        }
    }

    private void writeAccessLog(HttpServletRequest request, HttpServletResponse response, long startedAt) {
        if (request.getRequestURI().startsWith(HEALTH_PATH)) {
            return;
        }

        log.info("{} {} {} {}ms{}",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                (System.nanoTime() - startedAt) / 1_000_000,
                authenticatedAdmin());
    }

    private String authenticatedAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "";
        }
        return " admin=" + authentication.getName();
    }

    private String newRequestId() {
        return UUID.randomUUID().toString().substring(0, REQUEST_ID_LENGTH);
    }
}
