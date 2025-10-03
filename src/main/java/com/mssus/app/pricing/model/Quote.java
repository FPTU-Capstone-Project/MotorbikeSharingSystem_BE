package com.mssus.app.pricing.model;

import com.mssus.app.entity.PricingConfig;

import java.time.Instant;
import java.util.UUID;

public record Quote(
    UUID quoteId,
    int riderId,
    double pickupLat,
    double pickupLng,
    double dropoffLat,
    double dropoffLng,
    long distanceM,
    long durationS,
    String polyline,
    int pricingConfigId,
    FareBreakdown fare,
    Instant createdAt,
    Instant expiresAt
) {}

