package org.di.digital.config.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPrincipalHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String email = (String) attributes.get("email");

        if (email == null) {
            log.warn("WS Principal creation failed: no email in attributes");
            return null;
        }

        log.info("WS Principal created for: {}", email);
        return new UsernamePasswordAuthenticationToken(email, null);
    }
}
