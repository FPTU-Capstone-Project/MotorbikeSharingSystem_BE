package com.mssus.app.dto.response.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Response for pending payout information")
public class PendingPayoutResponse {

    @Schema(description = "Payout reference ID", example = "PAYOUT-123456")
    private String payoutRef;

    @Schema(description = "Payout amount", example = "500000")
    private BigDecimal amount;

    @Schema(description = "Bank name", example = "Vietcombank")
    private String bankName;

    @Schema(description = "Bank account number (masked)", example = "****7890")
    private String maskedAccountNumber;

    @Schema(description = "Account holder name", example = "NGUYEN VAN A")
    private String accountHolderName;

    @Schema(description = "User email", example = "user@example.com")
    private String userEmail;

    @Schema(description = "User ID", example = "1")
    private Integer userId;

    @Schema(description = "Status", example = "PENDING")
    private String status;

    @Schema(description = "Request date", example = "2025-01-15T10:30:00")
    private LocalDateTime requestedAt;
}






