package com.mssus.app.service.pricing.config;

import com.mssus.app.service.pricing.model.MoneyVnd;

public record FareTierDomain(
    Integer fareTierId,
    Integer tierLevel,
    Integer minKm,
    Integer maxKm,
    MoneyVnd amount
) {
}