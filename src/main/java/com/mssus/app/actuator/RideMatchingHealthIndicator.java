package com.mssus.app.actuator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Health indicator for the ride matching message queue infrastructure.
 * Checks connectivity to RabbitMQ and Redis, and reports on queue depths.
 */
@Component("rideMatching")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.messaging.ride", name = "enabled", havingValue = "true")
public class RideMatchingHealthIndicator implements HealthIndicator {

    private final RabbitTemplate rabbitTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        try {
            // Check RabbitMQ connectivity
            checkRabbitMQ(builder);
            
            // Check Redis connectivity
            checkRedis(builder);
            
        } catch (Exception e) {
            log.error("Health check failed", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }

        return builder.build();
    }

    private void checkRabbitMQ(Health.Builder builder) {
        try {
            // Simple connectivity check via template
            rabbitTemplate.getConnectionFactory().createConnection().close();
            builder.withDetail("rabbitmq", "UP");
            
            // Future: Add queue depth monitoring
            // Integer queueDepth = getQueueDepth("ride.matching.command.queue");
            // builder.withDetail("commandQueueDepth", queueDepth);
            
        } catch (Exception e) {
            log.warn("RabbitMQ health check failed", e);
            builder.down().withDetail("rabbitmq", "DOWN - " + e.getMessage());
        }
    }

    private void checkRedis(Health.Builder builder) {
        try {
            // Ping Redis
            String pong = redisTemplate.getConnectionFactory()
                .getConnection()
                .ping();
            
            if ("PONG".equalsIgnoreCase(pong)) {
                builder.withDetail("redis", "UP");
                
                // Count active matching sessions
                Integer sessionCount = redisTemplate.keys("ride:matching:session:*").size();
                builder.withDetail("activeSessions", sessionCount);
            } else {
                builder.down().withDetail("redis", "UNEXPECTED_RESPONSE - " + pong);
            }
            
        } catch (Exception e) {
            log.warn("Redis health check failed", e);
            builder.down().withDetail("redis", "DOWN - " + e.getMessage());
        }
    }
}

