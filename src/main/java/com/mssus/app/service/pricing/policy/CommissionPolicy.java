package com.mssus.app.service.pricing.policy;

import com.mssus.app.service.pricing.model.FareBreakdown;
import com.mssus.app.service.pricing.model.MoneyVnd;
import com.mssus.app.service.pricing.model.SettlementResult;

import java.math.BigDecimal;

public interface CommissionPolicy {
    SettlementResult settle(FareBreakdown fare);

    MoneyVnd cancelFee(FareBreakdown fare);
}

