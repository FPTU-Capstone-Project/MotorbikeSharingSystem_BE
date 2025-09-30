package com.mssus.app.dto.request.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request to initiate a top-up transaction")
public class TopUpInitRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Schema(description = "Amount to top up", example = "100000", required = true)
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    @Schema(description = "Payment method (MOMO, VNPAY)", example = "MOMO", required = true)
    private String paymentMethod;

    @Schema(description = "Return URL after payment", example = "https://app.example.com/wallet/callback")
    private String returnUrl;
}