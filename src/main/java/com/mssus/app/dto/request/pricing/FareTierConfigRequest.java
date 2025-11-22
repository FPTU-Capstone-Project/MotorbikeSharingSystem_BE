package com.mssus.app.dto.request.pricing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record FareTierConfigRequest(
    @NotNull @Min(1) Integer tierLevel,
    @NotNull @Min(0) Integer minKm,
    @NotNull @Min(1) @Max(25) Integer maxKm,
    @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal amount,
    String description
) {}
