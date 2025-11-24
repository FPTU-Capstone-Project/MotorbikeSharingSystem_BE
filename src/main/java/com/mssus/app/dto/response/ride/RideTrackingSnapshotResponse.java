package com.mssus.app.dto.response.ride;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RideTrackingSnapshotResponse(
    Integer rideId,
    Double driverLat,
    Double driverLng,
    Double riderLat,
    Double riderLng,
    String requestStatus,
    String rideStatus,
    String polyline,
    boolean detoured,
    LocalDateTime estimatedArrival
) {}
