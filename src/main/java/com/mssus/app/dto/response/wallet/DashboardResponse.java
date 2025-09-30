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
@Schema(description = "Wallet dashboard statistics")
public class DashboardResponse {

    @Schema(description = "Total active wallets", example = "1500")
    private Integer totalActiveWallets;

    @Schema(description = "Total wallet balance", example = "50000000")
    private BigDecimal totalWalletBalance;

    @Schema(description = "Total top-ups today", example = "25000000")
    private BigDecimal totalTopupsToday;

    @Schema(description = "Total payouts today", example = "10000000")
    private BigDecimal totalPayoutsToday;

    @Schema(description = "Pending transactions count", example = "45")
    private Integer pendingTransactionsCount;

    @Schema(description = "Total transactions today", example = "250")
    private Integer totalTransactionsToday;

    @Schema(description = "Average wallet balance", example = "33333")
    private BigDecimal avgWalletBalance;

    @Schema(description = "Total commission collected", example = "5000000")
    private BigDecimal totalCommissionCollected;
}