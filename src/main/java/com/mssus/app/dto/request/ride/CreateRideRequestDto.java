package com.mssus.app.dto.request.ride;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Request to create an AI-matched ride booking")
public record CreateRideRequestDto(
        
        @NotNull(message = "Quote ID is required")
        @Schema(description = "Quote ID from pricing service (ensures price transparency)", 
                example = "550e8400-e29b-41d4-a716-446655440000", required = true)
        UUID quoteId,
        
        @NotNull(message = "Pickup location ID is required")
        @Positive(message = "Pickup location ID must be positive")
        @Schema(description = "Pickup location ID (must match quote coordinates)", example = "1", required = true)
        Integer pickupLocationId,
        
        @NotNull(message = "Dropoff location ID is required")
        @Positive(message = "Dropoff location ID must be positive")
        @Schema(description = "Dropoff location ID (must match quote coordinates)", example = "2", required = true)
        Integer dropoffLocationId,
        
        @NotNull(message = "Pickup time is required")
        @Future(message = "Pickup time must be in the future")
        @Schema(description = "Desired pickup time (ISO 8601)", example = "2025-10-05T08:00:00", required = true)
        LocalDateTime pickupTime,
        
        @Schema(description = "Special requests or notes", example = "Need helmet")
        @Size(max = 500, message = "Special requests cannot exceed 500 characters")
        String notes
) {
}

