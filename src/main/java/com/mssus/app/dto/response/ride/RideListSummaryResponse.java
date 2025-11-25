package com.mssus.app.dto.response.ride;

import com.mssus.app.common.enums.RequestKind;
import com.mssus.app.common.enums.SharedRideRequestStatus;
import com.mssus.app.common.enums.SharedRideStatus;
import java.time.LocalDateTime;
import java.math.BigDecimal;

public record RideListSummaryResponse(
    Integer rideId,
    SharedRideStatus rideStatus,
    SharedRideRequestStatus requestStatus,
    RequestKind requestKind,
    String driverName,
    String riderName,
    String pickupAddress,
    String dropoffAddress,
    LocalDateTime scheduledTime,
    LocalDateTime createdAt,
    Float estimatedDistanceKm,
    Integer estimatedDurationMinutes,
    BigDecimal totalFare
) {}

