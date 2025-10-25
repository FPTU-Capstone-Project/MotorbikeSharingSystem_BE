package com.mssus.app.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.dto.domain.notification.DriverRideOfferNotification;
import com.mssus.app.dto.domain.notification.RiderMatchStatusNotification;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.User;
import com.mssus.app.service.NotificationService;
import com.mssus.app.service.RealTimeNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Domain-specific real-time notifications for ride matching events.
 *
 * <p>Hybrid approach: persist notifications via {@link NotificationService} for the in-app inbox
 * while also pushing richer payloads to dedicated STOMP destinations for live updates.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealTimeNotificationServiceImpl implements RealTimeNotificationService {

    private static final String DRIVER_QUEUE = "/queue/ride-offers";
    private static final String RIDER_QUEUE = "/queue/ride-matching";

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void notifyDriverOffer(DriverProfile driver, DriverRideOfferNotification payload) {
        try {
            // Send domain payload to driver's user-queue for actionable UI
            messagingTemplate.convertAndSendToUser(
                driver.getUser().getUserId().toString(),
                DRIVER_QUEUE,
                payload);

            notificationService.sendNotification(
                driver.getUser(),
                NotificationType.RIDE_REQUEST,
                "New shared ride request",
                String.format("Rider %s requested pickup at %s",
                    payload.getRiderName(),
                    payload.getPickupLocationName() == null ?
                        payload.getDropoffLat() + "," + payload.getDropoffLng() :
                        payload.getPickupLocationName()),
                toJson(payload),
                Priority.HIGH,
                DeliveryMethod.PUSH,
                DRIVER_QUEUE);
        } catch (Exception ex) {
            log.error("Failed to notify driver {} about ride offer {}", driver.getDriverId(), payload.getRequestId(), ex);
        }
    }

    @Override
    public void notifyRiderStatus(User riderUser, RiderMatchStatusNotification payload) {
        try {
            log.info("Sending rider notification to user {} - type: {}, message: {}",
                riderUser.getUserId(), payload.getClass().getSimpleName(), payload.getMessage());

            // Send domain payload to rider's user-queue for live status updates
            messagingTemplate.convertAndSendToUser(
                riderUser.getUserId().toString(),
                RIDER_QUEUE,
                payload);
            log.info("WebSocket message sent to /user/{}/queue/ride-matching", riderUser.getUserId());

            // Save notification to database
            notificationService.sendNotification(
                riderUser,
                NotificationType.RIDE_REQUEST,
                "Ride matching update",
                payload.getMessage(),
                toJson(payload),
                Priority.MEDIUM,
                DeliveryMethod.PUSH,
                RIDER_QUEUE);
            log.info("Database notification saved for user {}", riderUser.getUserId());

        } catch (Exception ex) {
            log.error("Failed to notify rider {} about request {}", riderUser.getUserId(), payload.getRequestId(), ex);
        }
    }

    @Override
    public void notifyDriverJoinRequest(DriverProfile driver, DriverRideOfferNotification payload) {
        try {
            // Send domain payload to driver's user-queue for actionable UI
            messagingTemplate.convertAndSendToUser(
                driver.getUser().getUserId().toString(),
                DRIVER_QUEUE,
                payload);

            notificationService.sendNotification(
                driver.getUser(),
                NotificationType.RIDE_REQUEST,
                "Join ride request",
                String.format("Rider %s wants to join your shared ride",
                    payload.getRiderName()),
                toJson(payload),
                Priority.HIGH,
                DeliveryMethod.PUSH,
                DRIVER_QUEUE);

            log.info("Join request notification sent to driver {} for request {}",
                driver.getDriverId(), payload.getRequestId());
        } catch (Exception ex) {
            log.error("Failed to notify driver {} about join request {}",
                driver.getDriverId(), payload.getRequestId(), ex);
        }
    }

    @Override
    public void notifyDriverTrackingStart(DriverProfile driver, Integer rideId) {
        try {
            String message = "Tracking started – share your ride en route!";
            String payloadJson = String.format("{\"rideId\": %d, \"action\": \"start_tracking\"}", rideId);

            messagingTemplate.convertAndSendToUser(
                driver.getUser().getUserId().toString(),
                DRIVER_QUEUE,
                Map.of("type", "TRACKING_START", "message", message, "rideId", rideId));

            notificationService.sendNotification(
                driver.getUser(),
                NotificationType.RIDE_TRACKING_START,
                "Tracking Started",
                message,
                payloadJson,
                Priority.HIGH,
                DeliveryMethod.PUSH,
                DRIVER_QUEUE);

            log.info("Tracking start notification sent to driver {} for ride {}", driver.getDriverId(), rideId);
        } catch (Exception ex) {
            log.error("Failed to notify driver {} about tracking start for ride {}", driver.getDriverId(), rideId, ex);
        }
    }


    private String toJson(Object payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(payload);
    }
}

