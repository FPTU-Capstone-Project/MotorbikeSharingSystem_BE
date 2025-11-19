package com.mssus.app.dto.request.route;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(description = "Payload to create a predefined template route")
public record CreateRouteTemplateRequest(
    @NotBlank
    @Schema(description = "Human friendly route name", example = "Gate 1 to Dormitory")
    String name,

    @Valid
    @NotNull
    @Schema(description = "Origin location descriptor")
    RouteEndpointRequest from,

    @Valid
    @NotNull
    @Schema(description = "Destination location descriptor")
    RouteEndpointRequest to,

    @Schema(description = "When the template becomes active. Defaults to now if omitted.")
    LocalDateTime validFrom,

    @Schema(description = "Optional expiry date for the template")
    LocalDateTime validUntil
) {}
