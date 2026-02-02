package org.di.digital.config.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
public class WebSocketEventListener {

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        String user = event.getUser() != null ? event.getUser().getName() : "anonymous";
        log.info("WS session connected: user={}, sessionId={}", user, event.getMessage().getHeaders().get("simpSessionId"));
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String user = event.getUser() != null ? event.getUser().getName() : "anonymous";
        log.info("WS session disconnected: user={}, sessionId={}", user, event.getSessionId());
    }
}

