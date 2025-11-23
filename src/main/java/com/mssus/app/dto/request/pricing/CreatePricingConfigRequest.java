package com.mssus.app.dto.request.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreatePricingConfigRequest(
    @NotNull @DecimalMin(value = "0.0", inclusive = true) @DecimalMax(value = "1.0", inclusive = true) BigDecimal systemCommissionRate,
    @Size(max = 255) String changeReason,
    @NotEmpty List<@Valid FareTierConfigRequest> fareTiers
) {}
