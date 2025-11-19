package com.mssus.app.dto.response.route;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Builder
@Schema(description = "Active pricing configuration overview for route planning")
public record PricingContextResponse(
    @Schema(description = "Pricing config id")
    Integer pricingConfigId,

    @Schema(description = "Version timestamp used by settlements")
    Instant version,

    @Schema(description = "Start of validity window")
    Instant validFrom,

    @Schema(description = "End of validity window, if any")
    Instant validUntil,

    @Schema(description = "System commission rate", example = "0.15")
    BigDecimal systemCommissionRate,

    @Schema(description = "Fare tiers applied when quoting")
    List<FareTierResponse> fareTiers
) {}
