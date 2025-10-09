package com.mssus.app.pricing;

import com.mssus.app.pricing.model.FareBreakdown;
import com.mssus.app.pricing.model.MoneyVnd;
import com.mssus.app.pricing.model.PriceInput;
import com.mssus.app.pricing.model.SettlementResult;

public interface PricingService {
    FareBreakdown quote(PriceInput input);               // used by /quotes
    SettlementResult settle(FareBreakdown agreedFare);   // used on ride complete
    MoneyVnd cancelFee(FareBreakdown agreedFare);        // late/no-show fee
}

