package com.mssus.app.dto.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payload pushed to riders reflecting the current booking state.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiderMatchStatusNotification {
    Integer requestId;
    String status;
    String message;
    Integer rideId;
    Integer driverId;
    String driverName;
    Float driverRating;
    String vehicleModel;
    String vehiclePlate;
    LocalDateTime estimatedPickupTime;
    LocalDateTime estimatedDropoffTime;
    BigDecimal totalFare;
}

