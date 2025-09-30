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
@Schema(description = "Response for initiated payout transaction")
public class PayoutInitResponse {

    @Schema(description = "Payout reference ID", example = "PAYOUT-123456")
    private String payoutRef;

    @Schema(description = "Payout amount", example = "500000")
    private BigDecimal amount;

    @Schema(description = "Status", example = "PROCESSING")
    private String status;

    @Schema(description = "Estimated completion time", example = "2025-01-15T10:30:00")
    private String estimatedCompletionTime;

    @Schema(description = "Bank account number (masked)", example = "****7890")
    private String maskedAccountNumber;
}