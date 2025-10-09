package com.mssus.app.pricing.model;

import java.math.BigDecimal;

public record FareBreakdown(
    String pricingVersion,
    long distanceMeters,
    long durationSeconds,
    MoneyVnd baseFlag,
    MoneyVnd perKmComponent,
    MoneyVnd perMinComponent,
    MoneyVnd surcharge,
    MoneyVnd discount,
    MoneyVnd subtotal,
    MoneyVnd total,
    BigDecimal commissionRate   // e.g., 0.10
) {}

