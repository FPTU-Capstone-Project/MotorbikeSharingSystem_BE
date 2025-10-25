package com.mssus.app.service.domain.pricing.policy.impl;

import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.service.domain.pricing.config.FareTierDomain;
import com.mssus.app.service.domain.pricing.config.PricingConfigDomain;
import com.mssus.app.service.domain.pricing.model.FareBreakdown;
import com.mssus.app.service.domain.pricing.model.MoneyVnd;
import com.mssus.app.service.domain.pricing.model.PriceInput;
import com.mssus.app.service.domain.pricing.model.PromoResult;
import com.mssus.app.service.domain.pricing.policy.FarePolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class StandardFarePolicy implements FarePolicy {
    private static final RoundingMode R = RoundingMode.HALF_UP;

    @Override
    public MoneyVnd computeSubtotal(PriceInput in, PricingConfigDomain cfg) {
        if (cfg.getFareTiers() == null || cfg.getFareTiers().isEmpty()) {
            throw BaseDomainException.of("pricing.validation.no-tiers-configured",
                Map.of("pricingConfigId", cfg.getPricingConfigId()));
        }

        var distanceKm = BigDecimal.valueOf(in.distanceMeters()).divide(BigDecimal.valueOf(1000), 3, R);

        List<FareTierDomain> sortedTiers = cfg.getFareTiers().stream()
            .sorted(Comparator.comparingInt(FareTierDomain::tierLevel))
            .toList();

        for (FareTierDomain tier : sortedTiers) {
            var tierMinKm = BigDecimal.valueOf(tier.minKm());
            var tierMaxKm = BigDecimal.valueOf(tier.maxKm());
            if (distanceKm.compareTo(tierMinKm) > 0  && distanceKm.compareTo(tierMaxKm) <= 0) {
                return tier.amount();
            }
        }

        throw BaseDomainException.of("pricing.validation.no-matching-tier", Map.of(
            "distanceKm", distanceKm,
            "pricingConfigId", cfg.getPricingConfigId()
        ));
    }

    @Override
    public FareBreakdown finalizeFare(PriceInput in, PricingConfigDomain cfg, MoneyVnd pre, PromoResult promo) {
//        var discount = promo.applied() ? promo.discount() : MoneyVnd.VND(0);
        var discount = MoneyVnd.VND(0); //TODO: promotion not implemented yet
        var unclamped = pre.sub(discount);
        var minFare = cfg.getFareTiers().stream().min(Comparator.comparingInt(FareTierDomain::tierLevel))
            .map(FareTierDomain::amount).orElse(MoneyVnd.VND(0));
        var total = MoneyVnd.VND(unclamped.amount().max(minFare.amount()));

        return new FareBreakdown(
            cfg.getVersion(),
            in.distanceMeters(),
            discount,
            pre,
            total,
            cfg.getSystemCommissionRate()
        );
    }
}
