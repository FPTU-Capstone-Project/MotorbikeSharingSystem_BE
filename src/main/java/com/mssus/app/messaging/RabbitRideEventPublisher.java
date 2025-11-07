package com.mssus.app.messaging;

import com.mssus.app.infrastructure.config.properties.RideMessagingProperties;
import com.mssus.app.messaging.dto.DriverLocationUpdateMessage;
import com.mssus.app.messaging.dto.RideRequestCreatedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@RequiredArgsConstructor
@Slf4j
public class RabbitRideEventPublisher implements RideEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RideMessagingProperties properties;

    @Override
    public void publishRideRequestCreated(Integer requestId) {
        if (requestId == null) {
            log.warn("Skipping ride.request.created publish - requestId is null");
            return;
        }

        RideRequestCreatedMessage message = RideRequestCreatedMessage.from(requestId);
        rabbitTemplate.convertAndSend(
            properties.getExchange(),
            properties.getRideRequestCreatedRoutingKey(),
            message);

        if (log.isDebugEnabled()) {
            log.debug("Published ride.request.created for request {}", requestId);
        }
    }

    @Override
    public void publishDriverLocationUpdate(DriverLocationUpdateMessage message) {
        if (message == null) {
            log.warn("Skipping driver location publish - message is null");
            return;
        }

        rabbitTemplate.convertAndSend(
            properties.getExchange(),
            properties.getDriverLocationRoutingKey(),
            message);

        if (log.isTraceEnabled()) {
            log.trace("Published driver location update for driver {} at ({}, {})",
                message.getDriverId(), message.getLatitude(), message.getLongitude());
        }
    }
}
