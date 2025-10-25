package com.mssus.app.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Ensures the Principal resolved during the JWT handshake is attached
 * to the WebSocket session so that convertAndSendToUser can route by name.
 */
@Component
@Slf4j
public class WebSocketUserHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(org.springframework.http.server.ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Object candidate = attributes.get(Principal.class.getName());
        if (candidate instanceof Principal principal) {
            log.debug("Using JWT Principal for WS session: {}", principal.getName());
            return principal;
        }
        Principal fallback = super.determineUser(request, wsHandler, attributes);
        log.debug("Fallback WS Principal: {}", fallback != null ? fallback.getName() : "null");
        return fallback;
    }
}

