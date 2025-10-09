package com.mssus.app.dto.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * Payload pushed to drivers when a new ride offer is available.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DriverRideOfferNotification {
    Integer requestId;
    Integer rideId;
    Integer driverId;
    String driverName;
    Integer riderId;
    String riderName;
    String pickupLocationName;
    String dropoffLocationName;
    Double pickupLat;
    Double pickupLng;
    Double dropoffLat;
    Double dropoffLng;
    LocalDateTime pickupTime;
    BigDecimal fareAmount;
    Float matchScore;
    Integer proposalRank;
    ZonedDateTime offerExpiresAt;
}
