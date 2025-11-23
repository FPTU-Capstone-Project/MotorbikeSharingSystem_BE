package com.mssus.app.dto.response.pricing;

import com.mssus.app.common.enums.PricingConfigStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
@Builder
public class PricingConfigResponse {
    Integer pricingConfigId;
    Instant version;
    BigDecimal systemCommissionRate;
    Instant validFrom;
    Instant validUntil;
    PricingConfigStatus status;
    String changeReason;
    Instant noticeSentAt;
    List<FareTierAdminResponse> fareTiers;
}
