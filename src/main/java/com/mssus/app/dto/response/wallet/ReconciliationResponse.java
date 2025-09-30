package com.mssus.app.dto.response.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Wallet reconciliation report")
public class ReconciliationResponse {

    @Schema(description = "Report generation time")
    private LocalDateTime generatedAt;

    @Schema(description = "Total system balance", example = "50000000")
    private BigDecimal totalSystemBalance;

    @Schema(description = "Total pending balance", example = "5000000")
    private BigDecimal totalPendingBalance;

    @Schema(description = "Total transactions count", example = "1500")
    private Integer totalTransactions;

    @Schema(description = "Mismatched wallets count", example = "0")
    private Integer mismatchedWallets;

    @Schema(description = "List of discrepancies")
    private List<WalletDiscrepancy> discrepancies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalletDiscrepancy {
        @Schema(description = "User ID")
        private Integer userId;

        @Schema(description = "Expected balance")
        private BigDecimal expectedBalance;

        @Schema(description = "Actual balance")
        private BigDecimal actualBalance;

        @Schema(description = "Difference")
        private BigDecimal difference;
    }
}