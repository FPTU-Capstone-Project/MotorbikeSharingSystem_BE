package com.mssus.app.messaging;

import com.mssus.app.dto.domain.notification.DriverRideOfferNotification;
import com.mssus.app.dto.domain.notification.RiderMatchStatusNotification;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.infrastructure.config.properties.RideMessagingProperties;
import com.mssus.app.messaging.dto.MatchingNotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.messaging.ride", name = {"enabled", "notifications-enabled"}, havingValue = "true")
public class RideNotificationEventPublisher {

    @Qualifier("rideEventRabbitTemplate")
    private final RabbitTemplate rabbitTemplate;
    private final RideMessagingProperties properties;

    public void publishDriverOffer(Integer requestId, DriverProfile driver, DriverRideOfferNotification payload) {
        if (driver == null || payload == null) {
            return;
        }
        MatchingNotificationMessage message = MatchingNotificationMessage.driverOffer(
            requestId,
            driver.getDriverId(),
            driver.getUser() != null ? driver.getUser().getUserId() : null,
            payload);
        rabbitTemplate.convertAndSend(
            properties.getExchange(),
            properties.getNotificationRoutingKey(),
            message);
        if (log.isTraceEnabled()) {
            log.trace("Published driver offer notification event for request {} driver {}", requestId, driver.getDriverId());
        }
    }

    public void publishRiderStatus(Integer requestId, Integer riderUserId, RiderMatchStatusNotification payload) {
        if (riderUserId == null || payload == null) {
            log.warn("Cannot publish rider status - missing riderUserId or payload");
            return;
        }
        MatchingNotificationMessage message = MatchingNotificationMessage.riderStatus(requestId, riderUserId, payload);
        rabbitTemplate.convertAndSend(
            properties.getExchange(),
            properties.getNotificationRoutingKey(),
            message);
        log.info("Published rider status notification to MQ for request {} rider {} - message: {}", 
            requestId, riderUserId, payload.getMessage());
    }
}
