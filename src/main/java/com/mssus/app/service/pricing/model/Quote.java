package com.mssus.app.service.pricing.model;

import java.time.Instant;
import java.util.UUID;

public record Quote(
    UUID quoteId,
    int riderId,
    Integer pickupLocationId,
    Integer dropoffLocationId,
    double pickupLat,
    double pickupLng,
    double dropoffLat,
    double dropoffLng,
    long distanceM,
    long durationS,
    String polyline,
    FareBreakdown fare,
    Instant createdAt,
    Instant expiresAt
) {}

