package com.mssus.app.config;

import com.mssus.app.security.JwtHandshakeInterceptor;
import com.mssus.app.security.WebSocketUserHandshakeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final WebSocketUserHandshakeHandler handshakeHandler;

    @Value("${spring.rabbitmq.stomp.host:localhost}")
    private String stompHost;

    @Value("${spring.rabbitmq.stomp.port:61613}")
    private int stompPort;

    @Value("${spring.rabbitmq.stomp.username:guest}")
    private String stompUsername;

    @Value("${spring.rabbitmq.stomp.password:guest}")
    private String stompPassword;

    @Value("${app.websocket.use-rabbitmq}")
    private boolean useRabbitMQ;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        System.out.println("WebSocketConfig: useRabbitMQ = " + useRabbitMQ);
        if (useRabbitMQ) {
            config.enableStompBrokerRelay("/topic", "/queue")
                .setRelayHost(stompHost)
                .setRelayPort(stompPort)
                .setClientLogin(stompUsername)
                .setClientPasscode(stompPassword)
                .setSystemLogin(stompUsername)
                .setSystemPasscode(stompPassword);
        } else {
            // Fallback to in-memory simple broker (for dev without RabbitMQ)
            config.enableSimpleBroker("/topic", "/queue");
        }

        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint
        registry.addEndpoint("/ws")
            .setHandshakeHandler(handshakeHandler)
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOrigins("http://localhost:3000", "http://localhost:8080", "http://127.0.0.1:8080", "http://localhost:63342", "http://127.0.0.1:5500")
            .withSockJS();

        registry.addEndpoint("/ws-native")
            .setHandshakeHandler(handshakeHandler)
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOrigins("*");
    }
}