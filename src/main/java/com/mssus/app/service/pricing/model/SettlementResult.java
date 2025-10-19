package com.mssus.app.service.pricing.model;

import com.mssus.app.entity.PricingConfig;

public record SettlementResult(
    MoneyVnd riderPay,       // = FareBreakdown.total
    MoneyVnd driverPayout,   // total * (1 - commission)
    MoneyVnd commission,      // total * commission
    PricingConfig pricingConfig
) {}

