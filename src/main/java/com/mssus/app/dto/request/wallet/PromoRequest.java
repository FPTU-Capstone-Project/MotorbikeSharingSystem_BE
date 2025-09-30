package com.mssus.app.dto.request.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "Request to distribute promotional credit")
public class PromoRequest {

    @Schema(description = "User IDs to receive promo (null for all users)")
    private List<Integer> userIds;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Schema(description = "Promo amount per user", example = "20000", required = true)
    private BigDecimal amount;

    @NotBlank(message = "Campaign name is required")
    @Schema(description = "Promotional campaign name", example = "New Year 2025", required = true)
    private String campaignName;

    @Schema(description = "User role filter", example = "RIDER", allowableValues = {"RIDER", "DRIVER"})
    private String userRole;
}