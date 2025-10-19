package com.mssus.app.service.pricing.policy.impl;

import com.mssus.app.repository.PricingConfigRepository;
import com.mssus.app.service.pricing.model.FareBreakdown;
import com.mssus.app.service.pricing.model.MoneyVnd;
import com.mssus.app.service.pricing.model.SettlementResult;
import com.mssus.app.service.pricing.policy.CommissionPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class StandardCommissionPolicy implements CommissionPolicy {
    private static final RoundingMode R = RoundingMode.HALF_UP;

    @Autowired
    private PricingConfigRepository pricingConfigRepository;

    @Override
    public SettlementResult settle(FareBreakdown fare) {
        var total = fare.total().amount();
        var commission = total.multiply(fare.commissionRate()).setScale(0, R);
        var driver = total.subtract(commission);
        var cfgEntity = pricingConfigRepository.findByVersion(fare.pricingVersion())
            .orElseThrow(() -> new IllegalStateException("PricingConfig not found for version: " + fare.pricingVersion()));
        return new SettlementResult(
            MoneyVnd.VND(total),
            MoneyVnd.VND(driver),
            MoneyVnd.VND(commission),
            cfgEntity
        );
    }

    @Override
    public MoneyVnd cancelFee(FareBreakdown fare) {
        // e.g., 20% cap 10,000 from fare.pricingVersion config (fetch if needed)
        // Simplest: encode constants in PricingConfig and read here
        // fee = min(rate * total, cap)
        var fee = fare.total().amount()
            .multiply(new BigDecimal("0.20"))
            .setScale(0, R);
        var capped = fee.min(BigDecimal.valueOf(10_000L));
        return MoneyVnd.VND(capped);
    }
}

