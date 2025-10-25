package com.mssus.app.service.domain.pricing.config;

import com.mssus.app.service.domain.pricing.model.MoneyVnd;

public record FareTierDomain(
    Integer fareTierId,
    Integer tierLevel,
    Integer minKm,
    Integer maxKm,
    MoneyVnd amount
) {
}