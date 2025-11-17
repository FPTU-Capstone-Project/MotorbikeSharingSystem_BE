package com.mssus.app.dto.response.route;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Resolved location details for a route endpoint")
public record RouteEndpointResponse(
    @Schema(description = "Location id", example = "12")
    Integer locationId,

    @Schema(description = "Display name", example = "FPT University - Gate 1")
    String name,

    @Schema(description = "Address string")
    String address,

    @Schema(description = "Latitude", example = "10.84148")
    Double latitude,

    @Schema(description = "Longitude", example = "106.809844")
    Double longitude,

    @Schema(description = "Whether the location is registered as a POI")
    Boolean isPoi
) {}
