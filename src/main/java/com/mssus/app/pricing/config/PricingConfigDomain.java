package com.mssus.app.pricing.config;

import com.mssus.app.pricing.model.MoneyVnd;

import java.math.BigDecimal;

public record PricingConfigDomain(
    Integer pricingConfigId,
    String version,
    MoneyVnd baseFlag,
    MoneyVnd perKm,
    MoneyVnd perMin,
    MoneyVnd minFare,
    MoneyVnd peakSurcharge,
    BigDecimal defaultCommission
) {}

