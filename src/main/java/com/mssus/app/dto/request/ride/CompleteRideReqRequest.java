package com.mssus.app.dto.request.ride;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request to complete a ride request associated with a ride")
public record CompleteRideReqRequest(
        @NotNull(message = "Ride ID is required")
        @Positive(message = "Ride ID must be positive")
        @Schema(description = "Unique identifier of the ride to be completed", example = "123", required = true)
        Integer rideId,

        @Positive(message = "Ride Request ID must be positive")
        @Schema(description = "Unique identifier of the ride request to be completed. Optional when the ride has at most one ongoing request.", example = "456")
        Integer rideRequestId) {
}
