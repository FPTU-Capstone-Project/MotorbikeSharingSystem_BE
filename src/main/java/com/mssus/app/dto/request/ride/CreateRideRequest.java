package com.mssus.app.dto.request.ride;

import com.mssus.app.dto.ride.LatLng;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

@Schema(description = "Request to create a new shared ride")
public record CreateRideRequest(
        @Positive(message = "Start location ID must be positive")
        @Schema(description = "Starting location ID", example = "1")
        Integer startLocationId,

        @Positive(message = "End location ID must be positive")
        @Schema(description = "Destination location ID", example = "2")
        Integer endLocationId,

        LatLng startLatLng,

        LatLng endLatLng,

        @Future(message = "Scheduled time must be in the future")
        @Schema(description = "Scheduled departure time (ISO 8601)", example = "2025-10-05T08:00:00", required = true)
        LocalDateTime scheduledDepartureTime
) {
}

