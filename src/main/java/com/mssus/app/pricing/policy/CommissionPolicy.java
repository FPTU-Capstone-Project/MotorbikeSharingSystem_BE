package com.mssus.app.pricing.policy;

import com.mssus.app.pricing.model.FareBreakdown;
import com.mssus.app.pricing.model.MoneyVnd;
import com.mssus.app.pricing.model.SettlementResult;

import java.math.BigDecimal;

public interface CommissionPolicy {
    SettlementResult settle(FareBreakdown fare, BigDecimal commissionRate);

    MoneyVnd cancelFee(FareBreakdown fare);
}

