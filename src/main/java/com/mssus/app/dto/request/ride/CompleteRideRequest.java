package com.mssus.app.dto.request.ride;

import com.mssus.app.dto.ride.LatLng;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request to complete a ride")
public record CompleteRideRequest(
    @NotNull(message = "Ride ID is required")
    @Positive(message = "Ride ID must be positive")
    @Schema(description = "Unique identifier of the ride to be started", example = "123", required = true)
    Integer rideId,
    LatLng currentDriverLocation
    ) {}
