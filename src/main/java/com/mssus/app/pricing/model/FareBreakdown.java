package com.mssus.app.pricing.model;

import java.math.BigDecimal;
import java.time.Instant;

public record FareBreakdown(
    Instant pricingVersion,
    long distanceMeters,
    MoneyVnd base2KmVnd,
    MoneyVnd after2KmPerKmVnd,
    MoneyVnd discount,
    MoneyVnd subtotal,
    MoneyVnd total,
    BigDecimal commissionRate
) {}

