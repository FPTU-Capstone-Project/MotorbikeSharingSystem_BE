package com.mssus.app.service.domain.pricing.impl;

import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.entity.FareTier;
import com.mssus.app.mapper.PricingConfigMapper;
import com.mssus.app.repository.FareTierRepository;
import com.mssus.app.service.domain.pricing.PricingService;
import com.mssus.app.service.domain.pricing.config.FareTierDomain;
import com.mssus.app.service.domain.pricing.model.FareBreakdown;
import com.mssus.app.service.domain.pricing.model.MoneyVnd;
import com.mssus.app.service.domain.pricing.model.PriceInput;
import com.mssus.app.service.domain.pricing.model.SettlementResult;
import com.mssus.app.service.domain.pricing.policy.CommissionPolicy;
import com.mssus.app.service.domain.pricing.policy.FarePolicy;
import com.mssus.app.service.domain.pricing.policy.PromotionPolicy;
import com.mssus.app.repository.PricingConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {
    private final PricingConfigRepository cfgRepo;
    private final FarePolicy farePolicy;
    private final PromotionPolicy promoPolicy;
    private final CommissionPolicy commissionPolicy;
    private final PricingConfigMapper pricingConfigMapper;
    private final FareTierRepository fareTierRepository;

    @Override
    public FareBreakdown quote(PriceInput in) {
        var cfgEntity = cfgRepo.findActive(Instant.now())
            .orElseThrow(() -> BaseDomainException.of("pricing-config.not-found.resource"));
        List<FareTierDomain> tierDomains = new ArrayList<>();

        fareTierRepository.findByPricingConfig_PricingConfigId(cfgEntity.getPricingConfigId()).stream()
            .filter(tier -> tier.getIsActive() == null || Boolean.TRUE.equals(tier.getIsActive()))
            .forEach(tier -> {
                tierDomains.add(new FareTierDomain(
                    tier.getFareTierId(),
                    tier.getTierLevel(),
                    tier.getMinKm(),
                    tier.getMaxKm(),
                    MoneyVnd.VND(tier.getAmount())
                ));
            });

        var cfg = pricingConfigMapper.toDomain(cfgEntity);
        cfg.setFareTiers(tierDomains);

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

