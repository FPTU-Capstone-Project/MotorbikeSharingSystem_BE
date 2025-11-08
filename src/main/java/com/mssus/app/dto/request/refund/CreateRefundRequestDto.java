package com.mssus.app.dto.request.refund;

import com.mssus.app.common.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to create a new refund request for admin review")
public class CreateRefundRequestDto {

    @NotNull(message = "Booking ID is required")
    @Schema(description = "Booking ID associated with the refund", example = "456", required = true)
    private Integer bookingId;

    @NotNull(message = "Transaction ID is required")
    @Schema(description = "Original transaction ID to refund", example = "789", required = true)
    private Integer transactionId;

    @NotNull(message = "Refund type is required")
    @Schema(description = "Type of refund (TOPUP, PAYOUT, CAPTURE_FARE, PROMO_CREDIT, ADJUSTMENT)", 
            example = "CAPTURE_FARE", required = true)
    private TransactionType refundType;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Schema(description = "Refund amount", example = "50000", required = true)
    private BigDecimal amount;

    @NotNull(message = "Reason is required")
    @Schema(description = "Reason for the refund request", 
            example = "Service quality issue - partial refund for incomplete ride", required = true)
    private String reason;

    @Schema(description = "PSP reference for payment tracking", example = "PSP-123456-789")
    private String pspRef;
}





