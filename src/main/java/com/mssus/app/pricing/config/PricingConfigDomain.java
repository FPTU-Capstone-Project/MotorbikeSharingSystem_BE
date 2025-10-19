package com.mssus.app.pricing.config;

import com.mssus.app.pricing.model.MoneyVnd;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public record PricingConfigDomain(
    Integer pricingConfigId,
    Instant version,
    MoneyVnd base2KmVnd,
    MoneyVnd after2KmPerKmVnd,
    BigDecimal systemCommissionRate,
    Instant validFrom,
    Instant validUntil
) {}

