package com.mssus.app.dto.request.route;

import com.mssus.app.dto.domain.ride.LatLng;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Represents one endpoint of a predefined route selected on the map.
 * Either {@code locationId} or {@code coordinates} must be provided.
 */
@Schema(description = "Route endpoint definition. Provide an existing location id or coordinates.")
public record RouteEndpointRequest(
    @Schema(description = "Existing location to reuse", example = "12")
    Integer locationId,

    @Valid
    @Schema(description = "Coordinates selected from the map")
    LatLng coordinates,

    @Schema(description = "Friendly label coming from the UI", example = "FPT University Gate 1")
    @Size(max = 255, message = "Label must be at most 255 characters")
    String label,

    @Schema(description = "Address resolved on the client to improve UX", example = "600 Nguyen Xi, Thu Duc")
    @Size(max = 500, message = "Address must be at most 500 characters")
    String address
) {

    public boolean hasLocationId() {
        return locationId != null;
    }

    public boolean hasCoordinates() {
        return coordinates != null && coordinates.latitude() != null && coordinates.longitude() != null;
    }

    public String labelOrDefault(String fallback) {
        return (label != null && !label.isBlank()) ? label : fallback;
    }
}
