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
@Schema(description = "Generic wallet operation response")
public class WalletOperationResponse {

    @Schema(description = "Operation success status", example = "true")
    private Boolean success;

    @Schema(description = "Transaction ID", example = "12345")
    private Integer transactionId;

    @Schema(description = "Operation message", example = "Funds captured successfully")
    private String message;

    @Schema(description = "New available balance", example = "450000")
    private BigDecimal newAvailableBalance;

    @Schema(description = "New pending balance", example = "0")
    private BigDecimal newPendingBalance;
}