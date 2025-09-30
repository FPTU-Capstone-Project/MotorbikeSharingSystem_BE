package com.mssus.app.dto.response.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response for initiated top-up transaction")
public class TopUpInitResponse {

    @Schema(description = "Transaction reference ID", example = "TXN-123456")
    private String transactionRef;

    @Schema(description = "Payment URL to redirect user", example = "https://payment.momo.vn/pay/...")
    private String paymentUrl;

    @Schema(description = "QR code URL for payment", example = "https://api.qrserver.com/...")
    private String qrCodeUrl;

    @Schema(description = "Deep link for mobile app", example = "momo://pay?...")
    private String deepLink;

    @Schema(description = "Transaction status", example = "PENDING")
    private String status;

    @Schema(description = "Expiry time in seconds", example = "900")
    private Integer expirySeconds;
}