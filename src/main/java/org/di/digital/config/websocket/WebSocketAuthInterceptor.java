package org.di.digital.config.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.security.jwt.JwtTokenUtil;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {
    private final JwtTokenUtil jwtTokenUtil;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }

        String token = extractToken(servletRequest);
        String email = extractAndValidateEmail(token, servletRequest);

        if (email == null) {
            log.warn("WS handshake rejected: no valid authentication");
            return false;
        }

        attributes.put("email", email);
        if (token != null) {
            attributes.put("token", token);
        }

        log.info("WS handshake accepted for user: {}", email);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        if (exception != null) {
            log.error("WS handshake error: {}", exception.getMessage());
        }
    }

    private String extractToken(ServletServerHttpRequest servletRequest) {
        // Try Authorization header first
        String authHeader = servletRequest.getServletRequest().getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Fallback to query parameter
        String query = servletRequest.getServletRequest().getQueryString();
        if (query != null) {
            return extractQueryParam(query, "token");
        }

        return null;
    }

    private String extractAndValidateEmail(String token, ServletServerHttpRequest servletRequest) {
        if (token != null) {
            try {
                if (jwtTokenUtil.validateJwtToken(token)) {
                    String email = jwtTokenUtil.getUsernameFromJwtToken(token);
                    if (email != null) {
                        log.info("WS email extracted from JWT: {}", email);
                        return email;
                    }
                } else {
                    log.warn("WS invalid JWT token");
                }
            } catch (Exception e) {
                log.error("WS JWT validation error: {}", e.getMessage());
            }
        }

        String query = servletRequest.getServletRequest().getQueryString();
        if (query != null) {
            String email = extractQueryParam(query, "email");
            if (email != null) {
                log.warn("WS email from query parameter (insecure): {}", email);
                return email;
            }
        }

        return null;
    }

    private String extractQueryParam(String query, String name) {
        for (String part : query.split("&")) {
            if (part.startsWith(name + "=")) {
                return URLDecoder.decode(
                        part.substring(name.length() + 1),
                        StandardCharsets.UTF_8
                );
            }
        }
        return null;
    }
}
