package com.mssus.app.pricing.policy;

import com.mssus.app.pricing.config.PricingConfigDomain;
import com.mssus.app.pricing.model.MoneyVnd;
import com.mssus.app.pricing.model.PriceInput;
import com.mssus.app.pricing.model.PromoResult;

public interface PromotionPolicy {
    PromoResult apply(PriceInput in, MoneyVnd subtotal, PricingConfigDomain cfg);
}
