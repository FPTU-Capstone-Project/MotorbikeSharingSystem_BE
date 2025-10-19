package com.mssus.app.service.pricing.policy.impl;

import com.mssus.app.service.pricing.config.PricingConfigDomain;
import com.mssus.app.service.pricing.model.FareBreakdown;
import com.mssus.app.service.pricing.model.MoneyVnd;
import com.mssus.app.service.pricing.model.PriceInput;
import com.mssus.app.service.pricing.model.PromoResult;
import com.mssus.app.service.pricing.policy.FarePolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class StandardFarePolicy implements FarePolicy {
    private static final RoundingMode R = RoundingMode.HALF_UP;

    @Override
    public MoneyVnd computeSubtotal(PriceInput in, PricingConfigDomain cfg) {
        var km = BigDecimal.valueOf(in.distanceMeters()).divide(BigDecimal.valueOf(1000), 3, R);

        var distance = cfg.after2KmPerKmVnd().mulBig(km.subtract(BigDecimal.valueOf(2)).max(BigDecimal.ZERO), R);
        var base = cfg.base2KmVnd();

        return base.add(distance);
    }

    @Override
    public FareBreakdown finalizeFare(PriceInput in, PricingConfigDomain cfg, MoneyVnd pre, PromoResult promo) {
//        var discount = promo.applied() ? promo.discount() : MoneyVnd.VND(0);
        var discount = MoneyVnd.VND(0); //TODO: promotion not implemented yet
        var unclamped = pre.sub(discount);
        var total = MoneyVnd.VND(unclamped.amount().max(cfg.base2KmVnd().amount()));

        return new FareBreakdown(
            cfg.version(),
            in.distanceMeters(),
            cfg.base2KmVnd(),
            cfg.after2KmPerKmVnd(),
            discount,
            pre,
            total,
            cfg.systemCommissionRate()
        );
    }
}

