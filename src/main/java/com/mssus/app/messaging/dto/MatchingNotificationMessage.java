package com.mssus.app.messaging.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mssus.app.dto.domain.notification.DriverRideOfferNotification;
import com.mssus.app.dto.domain.notification.RiderMatchStatusNotification;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class MatchingNotificationMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    MatchingNotificationType type;
    Integer requestId;
    Integer driverId;
    Integer driverUserId;
    Integer riderUserId;
    DriverRideOfferNotification driverPayload;
    RiderMatchStatusNotification riderPayload;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant occurredAt;
    String correlationId;

    public static MatchingNotificationMessage driverOffer(Integer requestId,
                                                           Integer driverId,
                                                           Integer driverUserId,
                                                           DriverRideOfferNotification payload) {
        return MatchingNotificationMessage.builder()
            .type(MatchingNotificationType.DRIVER_OFFER)
            .requestId(requestId)
            .driverId(driverId)
            .driverUserId(driverUserId)
            .driverPayload(payload)
            .occurredAt(Instant.now())
            .correlationId(UUID.randomUUID().toString())
            .build();
    }

    public static MatchingNotificationMessage riderStatus(Integer requestId,
                                                           Integer riderUserId,
                                                           RiderMatchStatusNotification payload) {
        return MatchingNotificationMessage.builder()
            .type(MatchingNotificationType.RIDER_STATUS)
            .requestId(requestId)
            .riderUserId(riderUserId)
            .riderPayload(payload)
            .occurredAt(Instant.now())
            .correlationId(UUID.randomUUID().toString())
            .build();
    }
}
