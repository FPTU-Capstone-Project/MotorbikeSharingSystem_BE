package com.mssus.app.service.domain.pricing.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record MoneyVnd(BigDecimal amount) {
    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    public MoneyVnd {
        Objects.requireNonNull(amount, "amount");
        amount = normalize(amount);
    }

    public static MoneyVnd VND(long v) {
        return new MoneyVnd(BigDecimal.valueOf(v));
    }

    public static MoneyVnd VND(BigDecimal value) {
        return new MoneyVnd(value);
    }

    public MoneyVnd add(MoneyVnd other) {
        Objects.requireNonNull(other, "other");
        return new MoneyVnd(this.amount.add(other.amount));
    }

    public MoneyVnd sub(MoneyVnd other) {
        Objects.requireNonNull(other, "other");
        return new MoneyVnd(this.amount.subtract(other.amount));
    }

    public MoneyVnd mulBig(BigDecimal factor, RoundingMode mode) {
        Objects.requireNonNull(factor, "factor");
        Objects.requireNonNull(mode, "roundingMode");
        var product = this.amount.multiply(factor).setScale(0, mode);
        return new MoneyVnd(product);
    }

    public long asLongExact() {
        return amount.longValueExact();
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value.setScale(0, DEFAULT_ROUNDING);
    }
}

