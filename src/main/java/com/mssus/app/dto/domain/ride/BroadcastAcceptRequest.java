package com.mssus.app.dto.domain.ride;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request payload for accepting a broadcasted ride request")
public record BroadcastAcceptRequest(
    @Positive(message = "Start location ID must be positive")
    @Schema(description = "Starting location ID", example = "1")
    Integer startLocationId,

    LatLng startLatLng
) {
}
