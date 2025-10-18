package com.mssus.app.dto.ride;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request payload for accepting a broadcasted ride request")
public record BroadcastAcceptRequest(
    @NotNull(message = "Vehicle ID is required")
    @Positive(message = "Vehicle ID must be positive")
    @Schema(description = "Selected vehicle ID for the new shared ride", example = "42")
    Integer vehicleId
) {
}
