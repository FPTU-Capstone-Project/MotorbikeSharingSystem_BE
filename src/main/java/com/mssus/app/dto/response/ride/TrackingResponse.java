package com.mssus.app.dto.response.ride;

import io.swagger.v3.oas.annotations.media.Schema;

public record TrackingResponse(
    @Schema(description = "Current distance traveled in km")
    double currentDistanceKm,

    @Schema(description = "Estimated time to arrival in minutes")
    int etaMinutes,

    @Schema(description = "Status of the tracking update")
    String status
) {}
