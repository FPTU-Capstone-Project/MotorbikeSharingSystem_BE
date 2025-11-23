package com.mssus.app.service.domain.pricing.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.mssus.app.common.enums.PricingConfigStatus;

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
    PricingConfigStatus status;
}
