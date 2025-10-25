package com.mssus.app.service.pricing.model;

import java.math.BigDecimal;
import java.time.Instant;

public record FareBreakdown(
    Instant pricingVersion,
    long distanceMeters,
    MoneyVnd discount,
    MoneyVnd subtotal,
    MoneyVnd total,
    BigDecimal commissionRate
) {}

