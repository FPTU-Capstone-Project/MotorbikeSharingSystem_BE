package com.mssus.app.dto.request.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Bank payout webhook callback request")
public class BankWebhookRequest {

    @Schema(description = "Transaction reference ID")
    private String transactionRef;

    @Schema(description = "Payout amount")
    private Long amount;

    @Schema(description = "Status code (SUCCESS, FAILED, PENDING)")
    private String status;

    @Schema(description = "Bank transaction ID")
    private String bankTransactionId;

    @Schema(description = "Error message if failed")
    private String errorMessage;

    @Schema(description = "Timestamp of completion")
    private String completedAt;

    @Schema(description = "Webhook signature")
    private String signature;
}