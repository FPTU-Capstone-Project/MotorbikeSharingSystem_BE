package com.mssus.app.pricing.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record MoneyVnd(long amount) {
    public static MoneyVnd VND(long v) {
        return new MoneyVnd(v);
    }

    public MoneyVnd add(MoneyVnd other) {
        return new MoneyVnd(this.amount + other.amount);
    }

    public MoneyVnd sub(MoneyVnd other) {
        return new MoneyVnd(this.amount - other.amount);
    }

    public MoneyVnd mulBig(BigDecimal factor, RoundingMode mode) {
        return new MoneyVnd(factor.multiply(BigDecimal.valueOf(amount)).setScale(0, mode).longValue());
    }
}

