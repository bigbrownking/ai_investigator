package org.di.digital.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DetailedAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        String path = request.getRequestURI();
        if (!isIgnorablePath(path)) {
            log.error("Authentication failed for {} {}: {}",
                    request.getMethod(),
                    path,
                    authException.getMessage());
        }

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpServletResponse.SC_UNAUTHORIZED)
                .error("Unauthorized")
                .message("Authentication required")
                .details(sanitizeErrorMessage(authException.getMessage()))
                .path(request.getRequestURI())
                .build();

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }

    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return "Please provide valid credentials";
        }

        if (message.toLowerCase().contains("token") || message.contains("JWT")) {
            return "Invalid or missing authentication token";
        }

        return "Authentication failed";
    }

    private boolean isIgnorablePath(String path) {
        return path.equals("/favicon.ico") ||
                path.equals("/robots.txt") ||
                path.equals("/apple-touch-icon.png") ||
                path.equals("/apple-touch-icon-precomposed.png");
    }
}

@Data
@Builder
class ErrorResponse {
    private LocalDateTime timestamp;
    private Integer status;
    private String error;
    private String message;
    private String details;
    private String path;
}