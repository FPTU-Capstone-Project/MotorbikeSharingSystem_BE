package com.mssus.app.service.domain.pricing.policy;

import com.mssus.app.service.domain.pricing.config.PricingConfigDomain;
import com.mssus.app.service.domain.pricing.model.FareBreakdown;
import com.mssus.app.service.domain.pricing.model.MoneyVnd;
import com.mssus.app.service.domain.pricing.model.PriceInput;
import com.mssus.app.service.domain.pricing.model.PromoResult;

public interface FarePolicy {
    MoneyVnd computeSubtotal(PriceInput in, PricingConfigDomain cfg);

    FareBreakdown finalizeFare(PriceInput in, PricingConfigDomain cfg, MoneyVnd preDiscount, PromoResult promo);
}

