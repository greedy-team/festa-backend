package com.greedy.festa.global.security;

import com.greedy.festa.global.exception.FestaException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_CODE_ATTRIBUTE = "festa.jwt.errorCode";

    /**
     * 이 요청을 수행한 관리자. 로그 패턴이 여기서 읽어 모든 줄에 붙이므로
     * (`logging.pattern.correlation`), 로그를 남기는 쪽은 아무것도 하지 않아도 된다.
     */
    public static final String ADMIN_MDC_KEY = "admin";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String accessToken = resolveToken(request);

        if (accessToken != null) {
            try {
                String username = jwtTokenProvider.parseUsername(accessToken);
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(username, null, List.of())
                );
                MDC.put(ADMIN_MDC_KEY, username);
            } catch (FestaException e) {
                request.setAttribute(ERROR_CODE_ATTRIBUTE, e.getErrorCode());
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 톰캣이 스레드를 재사용하므로 지우지 않으면 다음 요청의 로그에 이 관리자가 붙는다.
            MDC.remove(ADMIN_MDC_KEY);
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length());
    }
}
