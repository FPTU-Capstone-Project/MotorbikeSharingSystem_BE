package com.mssus.app.appconfig.config;

import com.mssus.app.appconfig.interceptor.JwtHandshakeInterceptor;
import com.mssus.app.appconfig.security.WebSocketUserHandshakeHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
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

//    private boolean useRabbitMQ = false;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        log.info("Configuring WebSocket message broker");
        log.debug("STOMP configuration - Host: {}, Port: {}, Username: {}", stompHost, stompPort, stompUsername);
//        System.out.println("WebSocketConfig: useRabbitMQ = " + useRabbitMQ);
//        if (useRabbitMQ) {
//            config.enableStompBrokerRelay("/topic", "/queue")
//                .setRelayHost(stompHost)
//                .setRelayPort(stompPort)
//                .setClientLogin(stompUsername)
//                .setClientPasscode(stompPassword)
//                .setSystemLogin(stompUsername)
//                .setSystemPasscode(stompPassword);
//        } else {
//            config.enableSimpleBroker("/topic", "/queue");
//        }
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");

        log.info("Message broker configured - Simple broker enabled with destinations: /topic, /queue");
        log.info("Application destination prefix: /app, User destination prefix: /user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        log.info("Registering STOMP endpoints");
        // WebSocket endpoint
        registry.addEndpoint("/ws")
            .setHandshakeHandler(handshakeHandler)
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOrigins("http://localhost:3000", "http://localhost:8080", "http://127.0.0.1:8080", "http://localhost:63342", "http://127.0.0.1:5500")
            .withSockJS();

        log.info("WebSocket endpoint '/ws' registered with SockJS fallback");
        log.debug("Allowed origins for /ws: http://localhost:3000, http://localhost:8080, http://127.0.0.1:8080, http://localhost:63342, http://127.0.0.1:5500");

        registry.addEndpoint("/ws-native")
            .setHandshakeHandler(handshakeHandler)
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOrigins("*");

        log.info("Native WebSocket endpoint '/ws-native' registered");
        log.warn("Native endpoint allows all origins (*) - ensure this is intended for your environment");

        log.info("WebSocket configuration completed successfully");
    }
}