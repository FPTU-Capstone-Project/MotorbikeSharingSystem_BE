package com.mssus.app.service.pricing.model;

import java.util.Optional;

public record PriceInput(
    long distanceMeters,
    Optional<String> promoCode,
    Integer riderId
) {}

