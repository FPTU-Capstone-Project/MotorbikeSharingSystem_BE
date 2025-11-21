package com.mssus.app.dto.request.wallet;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "PayOS payout webhook payload")
public class PayoutWebhookRequest {

    @JsonProperty("referenceId")
    @Schema(description = "Payout reference ID", example = "PAYOUT-1234567890")
    private String referenceId;

    @JsonProperty("status")
    @Schema(description = "Payout status: SUCCESS, FAILED, PROCESSING", example = "SUCCESS")
    private String status;

    @JsonProperty("amount")
    @Schema(description = "Payout amount", example = "500000")
    private Long amount;

    @JsonProperty("toAccountNumber")
    @Schema(description = "Destination account number (masked)", example = "****7890")
    private String toAccountNumber;

    @JsonProperty("toBin")
    @Schema(description = "Destination bank BIN", example = "970415")
    private String toBin;

    @JsonProperty("transactionId")
    @Schema(description = "PayOS transaction ID", example = "TXN-xxx")
    private String transactionId;

    @JsonProperty("completedAt")
    @Schema(description = "Completion timestamp", example = "2025-01-15T10:30:00Z")
    private LocalDateTime completedAt;

    @JsonProperty("failureReason")
    @Schema(description = "Failure reason if status is FAILED")
    private String failureReason;

    @JsonProperty("signature")
    @Schema(description = "Webhook signature for verification")
    private String signature;
}

