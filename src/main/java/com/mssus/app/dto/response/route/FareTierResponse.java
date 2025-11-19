package com.mssus.app.dto.response.route;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@Schema(description = "Fare tier definition of the active pricing config")
public record FareTierResponse(
    @Schema(description = "Tier level order", example = "1")
    Integer tierLevel,

    @Schema(description = "Minimum km covered by the tier", example = "0")
    Integer minKm,

    @Schema(description = "Maximum km covered by the tier", example = "2")
    Integer maxKm,

    @Schema(description = "Amount applied within the tier", example = "12000")
    BigDecimal amount,

    @Schema(description = "Optional tier description")
    String description
) {}
