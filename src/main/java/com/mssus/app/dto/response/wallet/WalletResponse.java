package com.mssus.app.dto.response.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {
    private Integer walletId;
    private Integer userId;
    private String pspAccountId;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal totalToppedUp;
    private BigDecimal totalSpent;
    private LocalDateTime lastSyncedAt;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalletSummary {
        private Integer walletId;
        private BigDecimal availableBalance;
        private BigDecimal pendingBalance;
        private Boolean isActive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalletBalance {
        private BigDecimal availableBalance;
        private BigDecimal pendingBalance;
        private BigDecimal totalBalance;
        private LocalDateTime lastUpdated;
    }
}

