package com.mssus.app.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.security.Principal;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtPrincipalExtractor jwtPrincipalExtractor;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        String token = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader;
            log.debug("Extracted token from header: {}", token.substring(0, 20) + "...");
        } else {
            String query = request.getURI().getQuery();
            if (query != null && query.contains("token=")) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("token=")) {
                        token = "Bearer " + param.substring(6);  // "token=eyJ..." -> "Bearer eyJ..."
                        log.debug("Extracted token from query: {}", token.substring(0, 20) + "...");
                        break;
                    }
                }
            }
        }

        if (token == null) {
            log.warn("No valid token found in WS handshake");
            return false;
        }

        try {
            UserDetails userDetails = jwtPrincipalExtractor.extractUserDetails(token);
            Principal principal = new UserPrincipal(userDetails);
            log.info("WS Handshake success: Principal name = {}", principal.getName());

            attributes.put(Principal.class.getName(), principal);

            return true;
        } catch (Exception e) {
            log.error("WS Handshake failed for token {}: {}", token.substring(0, 20) + "...", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("WS After-handshake error", exception);
        }
    }
}
