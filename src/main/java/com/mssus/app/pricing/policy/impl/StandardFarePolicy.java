package com.mssus.app.pricing.policy.impl;

import com.mssus.app.pricing.config.PricingConfigDomain;
import com.mssus.app.pricing.model.FareBreakdown;
import com.mssus.app.pricing.model.MoneyVnd;
import com.mssus.app.pricing.model.PriceInput;
import com.mssus.app.pricing.model.PromoResult;
import com.mssus.app.pricing.policy.FarePolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class StandardFarePolicy implements FarePolicy {
    private static final RoundingMode R = RoundingMode.HALF_UP;

    @Override
    public MoneyVnd computeSubtotal(PriceInput in, PricingConfigDomain cfg) {
        var km = BigDecimal.valueOf(in.distanceMeters()).divide(BigDecimal.valueOf(1000), 3, R);
        var mins = BigDecimal.valueOf(in.durationSeconds()).divide(BigDecimal.valueOf(60), 3, R);

        var distance = cfg.perKm().mulBig(km, R);      // Money × decimal
        var time = cfg.perMin().mulBig(mins, R);
        var base = cfg.baseFlag();
//        var sur = in.peak() ? cfg.peakSurcharge() : MoneyVnd.VND(0);

        return base.add(distance).add(time)/*.add(sur)*/;
    }

    @Override
    public FareBreakdown finalizeFare(PriceInput in, PricingConfigDomain cfg, MoneyVnd pre, PromoResult promo) {
//        var discount = promo.applied() ? promo.discount() : MoneyVnd.VND(0);
        var discount = MoneyVnd.VND(0); //TODO: promotion not implemented yet
        var unclamped = MoneyVnd.VND(pre.amount() - discount.amount());
        var total = MoneyVnd.VND(Math.max(unclamped.amount(), cfg.minFare().amount()));

        return new FareBreakdown(
            cfg.version(),
            in.distanceMeters(),
            in.durationSeconds(),
            cfg.baseFlag(),
            MoneyVnd.VND(pre.amount() - cfg.baseFlag().amount()
//                - (in.peak() ? cfg.peakSurcharge().amount() : 0)
                - computeTime(cfg, in).amount()),
            computeTime(cfg, in),
            /*in.peak() ? cfg.peakSurcharge() : */MoneyVnd.VND(0),
            discount,
            pre,
            total,
            cfg.defaultCommission()
        );
    }

    private MoneyVnd computeTime(PricingConfigDomain cfg, PriceInput in) {
        var mins = BigDecimal.valueOf(in.durationSeconds()).divide(BigDecimal.valueOf(60), 3, RoundingMode.HALF_UP);
        return cfg.perMin().mulBig(mins, RoundingMode.HALF_UP);
    }
}

