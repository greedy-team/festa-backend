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
        Throwable thrown = null;
        try {
            filterChain.doFilter(request, response);
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            writeAccessLog(request, response, startedAt, thrown);
            // 스레드가 재사용되므로, 비우지 않으면 다음 요청이 이 번호를 물려받는다.
            MDC.remove(REQUEST_ID);
        }
    }

    private void writeAccessLog(
            HttpServletRequest request, HttpServletResponse response, long startedAt, Throwable thrown
    ) {
        if (request.getRequestURI().startsWith(HEALTH_PATH)) {
            return;
        }

        log.info("{} {} {} {}ms{}{}",
                request.getMethod(),
                request.getRequestURI(),
                status(response, thrown),
                (System.nanoTime() - startedAt) / 1_000_000,
                authenticatedAdmin(),
                thrownMark(thrown));
    }

    /**
     * 예외가 체인 밖으로 빠져나가면 응답은 아직 나가지 않았고, 톰캣이 ERROR 디스패치에서 500을 쓴다.
     * 그 시점의 response.getStatus()는 아직 기본값 200이라, 그대로 적으면 터진 요청이 200으로 남는다.
     * 반대로 이미 나간 응답은 상태를 되돌릴 수 없으므로 실제로 나간 값을 적는다.
     */
    private int status(HttpServletResponse response, Throwable thrown) {
        if (thrown != null && !response.isCommitted()) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        return response.getStatus();
    }

    // 상태 코드 한 칸으로는 "터졌다"를 표현할 수 없다. 조사할 때 예외 종류가 첫 단서다.
    private String thrownMark(Throwable thrown) {
        if (thrown == null) {
            return "";
        }
        return " ex=" + thrown.getClass().getSimpleName();
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
