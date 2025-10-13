package com.mssus.app.pricing.policy.impl;

import com.mssus.app.pricing.model.FareBreakdown;
import com.mssus.app.pricing.model.MoneyVnd;
import com.mssus.app.pricing.model.SettlementResult;
import com.mssus.app.pricing.policy.CommissionPolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class StandardCommissionPolicy implements CommissionPolicy {
    private static final RoundingMode R = RoundingMode.HALF_UP;

    @Override
    public SettlementResult settle(FareBreakdown fare, BigDecimal rate) {
        var total = fare.total().amount();
        var commission = BigDecimal.valueOf(total).multiply(rate).setScale(0, R).longValue();
        var driver = total - commission;
        return new SettlementResult(MoneyVnd.VND(total), MoneyVnd.VND(driver), MoneyVnd.VND(commission));
    }

    @Override
    public MoneyVnd cancelFee(FareBreakdown fare) {
        // e.g., 20% cap 10,000 from fare.pricingVersion config (fetch if needed)
        // Simplest: encode constants in PricingConfig and read here
        // fee = min(rate * total, cap)
        return MoneyVnd.VND(Math.min(
            BigDecimal.valueOf(fare.total().amount()).multiply(new BigDecimal("0.20")).setScale(0, R).longValue(),
            10_000L
        ));
    }
}

