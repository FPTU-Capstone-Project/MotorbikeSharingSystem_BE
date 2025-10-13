package com.mssus.app.pricing.model;

import java.util.Optional;

public record PriceInput(
    long distanceMeters,
    long durationSeconds,
    Optional<String> promoCode,
    String riderId
) {}

