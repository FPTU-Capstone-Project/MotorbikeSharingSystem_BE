package com.mssus.app.dto.request.ride;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request to complete a ride request associated with a ride")
public record CompleteRideReqRequest(
    @NotNull(message = "Ride ID is required")
    @Positive(message = "Ride ID must be positive")
    @Schema(description = "Unique identifier of the ride to be started", example = "123", required = true)
    Integer rideId,

    @NotNull(message = "Ride Request ID is required")
    @Positive(message = "Ride Request ID must be positive")
    @Schema(description = "Unique identifier of the ride request to be completed", example = "456", required = true)
    Integer rideRequestId
) {}
