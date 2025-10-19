package com.mssus.app.dto.ride;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Request to accept a ride request")
public record AcceptRequestDto(
        
        @NotNull(message = "Ride ID is required")
        @Positive(message = "Ride ID must be positive")
        @Schema(description = "The ride ID accepting this request", example = "300", required = true)
        Integer rideId
//
//        @Future(message = "Estimated pickup time must be in the future")
//        @Schema(description = "Estimated pickup time (ISO 8601)", example = "2025-10-05T08:15:00")
//        LocalDateTime estimatedPickupTime
) {
}

