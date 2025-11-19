package com.mssus.app.dto.response.route;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
@Schema(description = "Computed pricing snapshot for a route definition")
public record RoutePricingPreviewResponse(
    @Schema(description = "Pricing configuration version timestamp")
    Instant pricingVersion,

    @Schema(description = "Subtotal fare before discounts")
    BigDecimal subtotal,

    @Schema(description = "Discount applied (if any)")
    BigDecimal discount,

    @Schema(description = "Final fare that becomes the route default price")
    BigDecimal total,

    @Schema(description = "Commission rate applied on settlements", example = "0.15")
    BigDecimal commissionRate
) {}
