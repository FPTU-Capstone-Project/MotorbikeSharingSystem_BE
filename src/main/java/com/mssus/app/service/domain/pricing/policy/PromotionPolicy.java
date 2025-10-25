package com.mssus.app.service.domain.pricing.policy;

import com.mssus.app.service.domain.pricing.config.PricingConfigDomain;
import com.mssus.app.service.domain.pricing.model.MoneyVnd;
import com.mssus.app.service.domain.pricing.model.PriceInput;
import com.mssus.app.service.domain.pricing.model.PromoResult;

public interface PromotionPolicy {
    PromoResult apply(PriceInput in, MoneyVnd subtotal, PricingConfigDomain cfg);
}
