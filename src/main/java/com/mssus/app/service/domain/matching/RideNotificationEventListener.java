package com.mssus.app.service.domain.matching;

import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.User;
import com.mssus.app.messaging.dto.MatchingNotificationMessage;
import com.mssus.app.messaging.dto.MatchingNotificationType;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.RealTimeNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.messaging.ride", name = {"enabled", "notifications-enabled"}, havingValue = "true")
public class RideNotificationEventListener {

    private final DriverProfileRepository driverProfileRepository;
    private final UserRepository userRepository;
    private final RealTimeNotificationService notificationService;

    @RabbitListener(queues = "${app.messaging.ride.notification-queue}", autoStartup = "true", concurrency = "1")
    public void onNotification(@Payload MatchingNotificationMessage message) {
        if (message == null || message.getType() == null) {
            return;
        }
        try {
            if (message.getType() == MatchingNotificationType.DRIVER_OFFER) {
                handleDriverOffer(message);
            } else if (message.getType() == MatchingNotificationType.RIDER_STATUS) {
                handleRiderStatus(message);
            } else {
                log.warn("Unhandled matching notification type {}", message.getType());
            }
        } catch (Exception ex) {
            log.error("Failed to process notification message {}: {}", message.getCorrelationId(), ex.getMessage(), ex);
        }
    }

    private void handleDriverOffer(MatchingNotificationMessage message) {
        if (message.getDriverId() == null || message.getDriverPayload() == null) {
            log.warn("Driver notification missing driverId/payload for request {}", message.getRequestId());
            return;
        }
        DriverProfile driver = driverProfileRepository.findById(message.getDriverId()).orElse(null);
        if (driver == null) {
            log.warn("Driver {} not found when dispatching notification for request {}", message.getDriverId(), message.getRequestId());
            return;
        }
        notificationService.notifyDriverOffer(driver, message.getDriverPayload());
    }

    private void handleRiderStatus(MatchingNotificationMessage message) {
        if (message.getRiderUserId() == null || message.getRiderPayload() == null) {
            log.warn("Rider notification missing riderUserId/payload for request {}", message.getRequestId());
            return;
        }
        log.info("Received rider status notification from MQ for request {} rider {} - message: {}", 
            message.getRequestId(), message.getRiderUserId(), 
            message.getRiderPayload() != null ? message.getRiderPayload().getMessage() : "null");
        
        User riderUser = userRepository.findById(message.getRiderUserId()).orElse(null);
        if (riderUser == null) {
            log.warn("Rider user {} not found when dispatching status for request {}", message.getRiderUserId(), message.getRequestId());
            return;
        }
        log.info("Dispatching rider status to notification service for user {}", riderUser.getUserId());
        notificationService.notifyRiderStatus(riderUser, message.getRiderPayload());
    }
}
