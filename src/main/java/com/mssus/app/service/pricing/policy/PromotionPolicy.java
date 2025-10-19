package com.mssus.app.service.pricing.policy;

import com.mssus.app.service.pricing.config.PricingConfigDomain;
import com.mssus.app.service.pricing.model.MoneyVnd;
import com.mssus.app.service.pricing.model.PriceInput;
import com.mssus.app.service.pricing.model.PromoResult;

public interface PromotionPolicy {
    PromoResult apply(PriceInput in, MoneyVnd subtotal, PricingConfigDomain cfg);
}
