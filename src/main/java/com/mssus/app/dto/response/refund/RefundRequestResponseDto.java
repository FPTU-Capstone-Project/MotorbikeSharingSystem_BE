package com.mssus.app.dto.response.refund;

import com.mssus.app.common.enums.RefundStatus;
import com.mssus.app.common.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response containing refund request details")
public class RefundRequestResponseDto {

    @Schema(description = "Unique refund request ID", example = "123")
    private Integer refundRequestId;

    @Schema(description = "UUID for refund request", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID refundRequestUuid;

    @Schema(description = "User ID who requested the refund", example = "456")
    private Integer userId;

    @Schema(description = "Associated booking ID", example = "789")
    private Integer bookingId;

    @Schema(description = "Original transaction ID", example = "101")
    private Integer transactionId;

    @Schema(description = "Type of refund", example = "CAPTURE_FARE")
    private TransactionType refundType;

    @Schema(description = "Refund amount", example = "50000")
    private BigDecimal amount;

    @Schema(description = "Currency code", example = "VND")
    private String currency;

    @Schema(description = "Current status of the refund request", example = "PENDING")
    private RefundStatus status;

    @Schema(description = "Reason for the refund request")
    private String reason;

    @Schema(description = "User ID who requested the refund", example = "456")
    private Integer requestedByUserId;

    @Schema(description = "User ID who reviewed the refund", example = "789")
    private Integer reviewedByUserId;

    @Schema(description = "Notes from the reviewer")
    private String reviewNotes;

    @Schema(description = "PSP reference", example = "PSP-123456")
    private String pspRef;

    @Schema(description = "When the request was created")
    private LocalDateTime createdAt;

    @Schema(description = "When the request was last updated")
    private LocalDateTime updatedAt;

    @Schema(description = "When the request was reviewed")
    private LocalDateTime reviewedAt;

    @Schema(description = "When the refund was completed")
    private LocalDateTime completedAt;
}





