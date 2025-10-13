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

        @Future(message = "Pickup time must be in the future")
        @Schema(description = "Desired pickup time (ISO 8601)", example = "2025-10-05T08:00:00")
        LocalDateTime desiredPickupTime,

        @Schema(description = "Special requests or notes", example = "Need helmet")
        @Size(max = 500, message = "Special requests cannot exceed 500 characters")
        String notes
) {
}

