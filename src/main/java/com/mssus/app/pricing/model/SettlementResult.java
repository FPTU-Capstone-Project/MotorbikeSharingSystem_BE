package com.mssus.app.pricing.model;

public record SettlementResult(
    MoneyVnd riderPay,       // = FareBreakdown.total
    MoneyVnd driverPayout,   // total * (1 - commission)
    MoneyVnd commission      // total * commission
) {}

