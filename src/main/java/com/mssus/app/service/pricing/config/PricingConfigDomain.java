package com.mssus.app.service.pricing.config;

import com.mssus.app.service.pricing.model.MoneyVnd;

import java.math.BigDecimal;
import java.time.Instant;

public record PricingConfigDomain(
    Integer pricingConfigId,
    Instant version,
    MoneyVnd base2KmVnd,
    MoneyVnd after2KmPerKmVnd,
    BigDecimal systemCommissionRate,
    Instant validFrom,
    Instant validUntil
) {}

