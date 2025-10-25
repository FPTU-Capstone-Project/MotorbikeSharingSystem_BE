package com.mssus.app.service.domain.pricing.model;

import com.mssus.app.entity.Location;

import java.time.Instant;
import java.util.UUID;

public record Quote(
    UUID quoteId,
    int riderId,
    Location pickupLocation,
    Location dropoffLocation,
    long distanceM,
    long durationS,
    String polyline,
    FareBreakdown fare,
    Instant createdAt,
    Instant expiresAt
) {}

