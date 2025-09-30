package com.mssus.app.dto.request.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "VNPay webhook callback request")
public class VnpayWebhookRequest {

    @Schema(description = "VNPay TmnCode")
    private String vnp_TmnCode;

    @Schema(description = "Transaction amount (in VND cents)")
    private Long vnp_Amount;

    @Schema(description = "Bank code")
    private String vnp_BankCode;

    @Schema(description = "Transaction info")
    private String vnp_OrderInfo;

    @Schema(description = "VNPay transaction number")
    private String vnp_TransactionNo;

    @Schema(description = "Response code (00 for success)")
    private String vnp_ResponseCode;

    @Schema(description = "Transaction status")
    private String vnp_TransactionStatus;

    @Schema(description = "Transaction reference")
    private String vnp_TxnRef;

    @Schema(description = "Secure hash")
    private String vnp_SecureHash;

    @Schema(description = "Payment date")
    private String vnp_PayDate;

    @Schema(description = "All parameters for signature verification")
    private Map<String, String> allParams;
}