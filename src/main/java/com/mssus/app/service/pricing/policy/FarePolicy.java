package com.mssus.app.service.pricing.policy;

import com.mssus.app.service.pricing.config.PricingConfigDomain;
import com.mssus.app.service.pricing.model.FareBreakdown;
import com.mssus.app.service.pricing.model.MoneyVnd;
import com.mssus.app.service.pricing.model.PriceInput;
import com.mssus.app.service.pricing.model.PromoResult;

public interface FarePolicy {
    MoneyVnd computeSubtotal(PriceInput in, PricingConfigDomain cfg);

    FareBreakdown finalizeFare(PriceInput in, PricingConfigDomain cfg, MoneyVnd preDiscount, PromoResult promo);
}

