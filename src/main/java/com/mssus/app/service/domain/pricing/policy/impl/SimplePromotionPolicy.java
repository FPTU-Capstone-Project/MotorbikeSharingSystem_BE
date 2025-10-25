package com.mssus.app.service.domain.pricing.policy.impl;

import com.mssus.app.service.domain.pricing.config.PricingConfigDomain;
import com.mssus.app.service.domain.pricing.model.MoneyVnd;
import com.mssus.app.service.domain.pricing.model.PriceInput;
import com.mssus.app.service.domain.pricing.model.PromoResult;
import com.mssus.app.service.domain.pricing.policy.PromotionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SimplePromotionPolicy implements PromotionPolicy {
    @Override
    public PromoResult apply(PriceInput in, MoneyVnd subtotal, PricingConfigDomain cfg) {
        throw new UnsupportedOperationException("Promotions not implemented yet");
    }


//    private final PromotionRepository promotionRepo;     // JPA for promotions
//    private final TransactionRepository txRepo;          // to count per-user usage
//
//    @Override
//    public PromoResult apply(PriceInput in, MoneyVnd subtotal, PricingConfig cfg) {
//        if (in.promoCode().isEmpty()) return new PromoResult(false, null, MoneyVnd.VND(0), "no_code");
//        var code = in.promoCode().get().trim().toUpperCase();
//
//        var promo = promotionRepo.findActiveByCodeAt(code, Instant.now());
//        if (promo.isEmpty()) return new PromoResult(false, code, MoneyVnd.VND(0), "expired_or_inactive");
//
//        // per-user usage (count transactions or a dedicated user_promotions if you have it)
//        var used = txRepo.countPromoUsageForUser(code, in.riderId()); // implement as needed
//        if (used >= promo.get().getUsageLimitPerUser()) return new PromoResult(false, code, MoneyVnd.VND(0), "limit_reached");
//
//        // percentage or fixed with cap
//        MoneyVnd discount = switch (promo.get().getDiscountType()) {
//            case PERCENTAGE -> {
//                var pct = promo.get().getDiscountRate(); // BigDecimal 0.10
//                var raw = BigDecimal.valueOf(subtotal.amount()).multiply(pct).setScale(0, RoundingMode.HALF_UP).longValue();
//                var capped = Math.min(raw, promo.get().getDiscountMaxVnd());
//                yield MoneyVnd.VND(capped);
//            }
//            case FIXED_AMOUNT -> MoneyVnd.VND(promo.get().getDiscountFixedVnd());
//        };
//
//        // (Optional) also ensure subtotal >= promo.min_amount_vnd
//        if (subtotal.amount() < promo.get().getMinRideAmountVnd()) return new PromoResult(false, code, MoneyVnd.VND(0), "below_min");
//
//        return new PromoResult(true, code, discount, "ok");
//    }
}

