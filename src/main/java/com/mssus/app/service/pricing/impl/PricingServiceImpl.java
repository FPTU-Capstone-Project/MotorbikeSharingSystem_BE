package com.mssus.app.service.pricing.impl;

import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.mapper.PricingConfigMapper;
import com.mssus.app.service.pricing.PricingService;
import com.mssus.app.service.pricing.model.FareBreakdown;
import com.mssus.app.service.pricing.model.MoneyVnd;
import com.mssus.app.service.pricing.model.PriceInput;
import com.mssus.app.service.pricing.model.SettlementResult;
import com.mssus.app.service.pricing.policy.CommissionPolicy;
import com.mssus.app.service.pricing.policy.FarePolicy;
import com.mssus.app.service.pricing.policy.PromotionPolicy;
import com.mssus.app.repository.PricingConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {
    private final PricingConfigRepository cfgRepo;
    private final FarePolicy farePolicy;
    private final PromotionPolicy promoPolicy;
    private final CommissionPolicy commissionPolicy;
    private final PricingConfigMapper pricingConfigMapper;

    @Override
    public FareBreakdown quote(PriceInput in) {
        var cfgEntity = cfgRepo.findActive(Instant.now())
            .orElseThrow(() -> BaseDomainException.of("pricing-config.not-found.resource"));

        var cfg = pricingConfigMapper.toDomain(cfgEntity);
        var preDiscount = farePolicy.computeSubtotal(in, cfg);
//        var promo = promoPolicy.apply(in, preDiscount, cfg);
        return farePolicy.finalizeFare(in, cfg, preDiscount, null);
    }

    @Override
    public SettlementResult settle(FareBreakdown agreed) {
        return commissionPolicy.settle(agreed);
    }

    @Override
    public MoneyVnd cancelFee(FareBreakdown agreed) {
        return commissionPolicy.cancelFee(agreed);
    }
}

