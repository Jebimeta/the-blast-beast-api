package com.theblastbeast.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theblastbeast.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RestSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String ERROR_TYPE_BASE = "https://theblastbeast.com/errors/";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        write(response, request, ErrorCode.AUTH_REQUIRED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        write(response, request, ErrorCode.ACCESS_DENIED);
    }

    private void write(HttpServletResponse response, HttpServletRequest request, ErrorCode errorCode)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getTitle());
        problem.setTitle(errorCode.getTitle());
        problem.setType(URI.create(ERROR_TYPE_BASE + errorCode.getCode().toLowerCase(Locale.ROOT)));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", errorCode.getCode());

        response.setStatus(HttpStatus.valueOf(errorCode.getStatus().value()).value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
