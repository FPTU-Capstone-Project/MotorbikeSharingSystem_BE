package com.mssus.app.dto.request;

import com.mssus.app.dto.LatLng;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

public record QuoteRequest(
    LatLng pickup,

    LatLng dropoff,

    @Positive(message = "Pickup location ID must be positive")
    @Schema(description = "Pickup location ID along the route", example = "5")
    Integer pickupLocationId,

    @Positive(message = "Dropoff location ID must be positive")
    @Schema(description = "Dropoff location ID along the route", example = "6")
    Integer dropoffLocationId) {}
