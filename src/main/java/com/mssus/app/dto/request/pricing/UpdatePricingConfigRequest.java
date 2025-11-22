package com.mssus.app.dto.request.pricing;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdatePricingConfigRequest(
    @DecimalMin(value = "0.0", inclusive = true) @DecimalMax(value = "1.0", inclusive = true) BigDecimal systemCommissionRate,
    @Size(max = 255) String changeReason
) {}
