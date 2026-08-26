package com.greedy.festa.global.security;

import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.ErrorCode;
import com.greedy.festa.global.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        ErrorCode errorCode = resolveErrorCode(request);

        ErrorResponse body = new ErrorResponse(
                errorCode.name(),
                errorCode.getMessage(),
                errorCode.getStatus().value(),
                request.getRequestURI()
        );

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private ErrorCode resolveErrorCode(HttpServletRequest request) {
        Object attribute = request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE);
        if (attribute instanceof ErrorCode errorCode) {
            return errorCode;
        }
        return CommonErrorCode.UNAUTHORIZED;
    }
}
