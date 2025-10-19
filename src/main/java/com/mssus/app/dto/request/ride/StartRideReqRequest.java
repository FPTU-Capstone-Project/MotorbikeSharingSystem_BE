package com.mssus.app.dto.request.ride;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request to mark an individual ride request as started")
public record StartRideReqRequest(
    @NotNull(message = "Ride ID is required")
    @Positive(message = "Ride ID must be positive")
    @Schema(description = "Shared ride identifier", example = "123", required = true)
    Integer rideId,

    @NotNull(message = "Ride Request ID is required")
    @Positive(message = "Ride Request ID must be positive")
    @Schema(description = "Shared ride request identifier", example = "456", required = true)
    Integer rideRequestId
) {
}
