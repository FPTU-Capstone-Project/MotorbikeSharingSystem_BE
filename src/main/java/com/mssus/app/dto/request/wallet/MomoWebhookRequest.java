package com.mssus.app.dto.request.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "MoMo webhook callback request")
public class MomoWebhookRequest {

    @Schema(description = "Partner code")
    private String partnerCode;

    @Schema(description = "Order ID")
    private String orderId;

    @Schema(description = "Request ID")
    private String requestId;

    @Schema(description = "Transaction amount")
    private Long amount;

    @Schema(description = "Order info")
    private String orderInfo;

    @Schema(description = "Order type")
    private String orderType;

    @Schema(description = "Transaction ID from MoMo")
    private Long transId;

    @Schema(description = "Result code (0 for success)")
    private Integer resultCode;

    @Schema(description = "Message from MoMo")
    private String message;

    @Schema(description = "Payment type")
    private String payType;

    @Schema(description = "Response time")
    private Long responseTime;

    @Schema(description = "Extra data")
    private String extraData;

    @Schema(description = "Signature")
    private String signature;
}