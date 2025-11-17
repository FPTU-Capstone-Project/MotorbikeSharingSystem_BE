package com.mssus.app.dto.response.route;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Detailed representation of a predefined route")
public record RouteDetailResponse(
    Integer routeId,
    String name,
    String routeType,
    RouteEndpointResponse from,
    RouteEndpointResponse to,
    BigDecimal defaultPrice,
    Long distanceMeters,
    Long durationSeconds,
    String polyline,
    LocalDateTime validFrom,
    LocalDateTime validUntil,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    RoutePricingPreviewResponse pricingPreview
) {}
