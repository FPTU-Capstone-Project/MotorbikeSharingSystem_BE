package com.mssus.app.dto.response.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Balance check response")
public class BalanceCheckResponse {

    @Schema(description = "User ID", example = "123")
    private Integer userId;

    @Schema(description = "Available balance", example = "500000")
    private BigDecimal availableBalance;

    @Schema(description = "Pending balance", example = "50000")
    private BigDecimal pendingBalance;

    @Schema(description = "Has sufficient funds", example = "true")
    private Boolean hasSufficientFunds;

    @Schema(description = "Requested amount", example = "100000")
    private BigDecimal requestedAmount;

    @Schema(description = "Wallet active status", example = "true")
    private Boolean isActive;
}