package com.mssus.app.controller;

import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.common.exception.catalog.ErrorCatalogService;
import com.mssus.app.common.exception.catalog.ErrorEntry;
import com.mssus.app.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TestController {
    @GetMapping("/test/websocket")
    public ResponseEntity<Map<String, Object>> testWebSocket() {
        try {
            // Check if WebSocket endpoint is accessible
            Map<String, Object> response = Map.of(
                "status", "success",
                "message", "WebSocket endpoint is configured",
                "websocket_url", "/ws",
                "test_instructions", Map.of(
                    "connect_to", "ws://localhost:8080/ws",
                    "subscribe_to", "/topic/test",
                    "send_to", "/app/test"
                )
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = Map.of(
                "status", "error",
                "message", "WebSocket test failed: " + e.getMessage()
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @MessageMapping("/test")
    @SendTo("/topic/test")
    public Map<String, Object> handleTestMessage(Map<String, Object> message) {
        return Map.of(
            "status", "received",
            "timestamp", System.currentTimeMillis(),
            "echo", message
        );
    }


}
