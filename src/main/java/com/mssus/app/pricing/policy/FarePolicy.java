package com.mssus.app.pricing.policy;

import com.mssus.app.pricing.config.PricingConfigDomain;
import com.mssus.app.pricing.model.FareBreakdown;
import com.mssus.app.pricing.model.MoneyVnd;
import com.mssus.app.pricing.model.PriceInput;
import com.mssus.app.pricing.model.PromoResult;

public interface FarePolicy {
    MoneyVnd computeSubtotal(PriceInput in, PricingConfigDomain cfg);

    FareBreakdown finalizeFare(PriceInput in, PricingConfigDomain cfg, MoneyVnd preDiscount, PromoResult promo);
}

