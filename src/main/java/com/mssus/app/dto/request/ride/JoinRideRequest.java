package com.mssus.app.dto.request.ride;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Request to join a specific shared ride")
public record JoinRideRequest(
        
        @NotNull(message = "Quote ID is required")
        @Schema(description = "Quote ID from pricing service (ensures price transparency)", 
                example = "550e8400-e29b-41d4-a716-446655440000", required = true)
        UUID quoteId,

        @Positive(message = "Pickup location ID must be positive")
        @Schema(description = "Pickup location ID along the route (must match quote)", example = "5")
        Integer pickupLocationId,

        @Positive(message = "Dropoff location ID must be positive")
        @Schema(description = "Dropoff location ID along the route (must match quote)", example = "6")
        Integer dropoffLocationId,

        @Future(message = "Pickup time must be in the future")
        @Schema(description = "Desired pickup time (ISO 8601)", example = "2025-10-05T08:30:00")
        LocalDateTime pickupTime,
        
        @Schema(description = "Special requests or notes", example = "Please wait at the gate")
        @Size(max = 500, message = "Special requests cannot exceed 500 characters")
        String specialRequests
) {
}

