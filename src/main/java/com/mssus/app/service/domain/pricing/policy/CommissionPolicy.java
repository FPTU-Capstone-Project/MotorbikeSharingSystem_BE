package com.mssus.app.service.domain.pricing.policy;

import com.mssus.app.service.domain.pricing.model.FareBreakdown;
import com.mssus.app.service.domain.pricing.model.MoneyVnd;
import com.mssus.app.service.domain.pricing.model.SettlementResult;

public interface CommissionPolicy {
    SettlementResult settle(FareBreakdown fare);

    MoneyVnd cancelFee(FareBreakdown fare);
}

