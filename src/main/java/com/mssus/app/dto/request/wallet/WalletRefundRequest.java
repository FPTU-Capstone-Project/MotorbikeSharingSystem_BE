package com.mssus.app.dto.request.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request to refund to user")
public class WalletRefundRequest {

    @NotNull(message = "User ID is required")
    @Schema(description = "User ID to refund", example = "123", required = true)
    private Integer userId;

    @NotNull(message = "Booking ID is required")
    @Schema(description = "Booking ID", example = "456", required = true)
    private Long bookingId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Schema(description = "Refund amount", example = "50000", required = true)
    private BigDecimal amount;

    @Schema(description = "Refund reason", example = "Service issue - partial refund")
    private String reason;
}