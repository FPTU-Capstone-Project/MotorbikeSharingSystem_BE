package com.mssus.app.service.pricing.config;

import com.mssus.app.service.pricing.model.MoneyVnd;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingConfigDomain {
    Integer pricingConfigId;
    Instant version;
    List<FareTierDomain> fareTiers;
    BigDecimal systemCommissionRate;
    Instant validFrom;
    Instant validUntil;
}

