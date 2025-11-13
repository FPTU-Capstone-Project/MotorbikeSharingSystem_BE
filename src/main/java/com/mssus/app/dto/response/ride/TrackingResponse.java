package com.mssus.app.dto.response.ride;

import io.swagger.v3.oas.annotations.media.Schema;

public record TrackingResponse(
    @Schema(description = "Current distance traveled in km")
    double currentDistanceKm,

    @Schema(description = "Encoded polyline the driver is supposed to follow.")
    String polyline,

    @Schema(description = "Status of the tracking update")
    String status,

    @Schema(description = "Latest driver latitude")
    Double driverLat,

    @Schema(description = "Latest driver longitude")
    Double driverLng,

    @Schema(description = "True if a detour polyline is being used")
    boolean detoured
) {}
