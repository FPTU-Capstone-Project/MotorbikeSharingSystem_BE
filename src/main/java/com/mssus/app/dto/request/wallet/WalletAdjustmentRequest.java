package com.mssus.app.dto.request.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request for admin wallet adjustment")
public class WalletAdjustmentRequest {

    @NotNull(message = "User ID is required")
    @Schema(description = "User ID", example = "123", required = true)
    private Integer userId;

    @NotNull(message = "Amount is required")
    @Schema(description = "Adjustment amount (positive or negative)", example = "10000", required = true)
    private BigDecimal amount;

    @NotBlank(message = "Reason is required")
    @Schema(description = "Reason for adjustment", example = "Manual correction for system error", required = true)
    private String reason;

    @Schema(description = "Adjustment type", example = "CORRECTION", allowableValues = {"CORRECTION", "BONUS", "PENALTY"})
    private String adjustmentType;
}