package com.mssus.app.dto.domain.ride;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Request to accept a ride request")
public record AcceptRequestDto(
        
        @NotNull(message = "Ride ID is required")
        @Positive(message = "Ride ID must be positive")
        @Schema(description = "The ride ID accepting this request", example = "300", required = true)
        Integer rideId,

        @NotNull(message = "Current driver location is required")
        @Schema(description = "The current location of the driver", required = true)
        LatLng currentDriverLocation
) {
}

