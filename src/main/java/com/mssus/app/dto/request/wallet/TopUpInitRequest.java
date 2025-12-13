package com.mssus.app.dto.request.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request to initiate a top-up transaction")
public class TopUpInitRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @DecimalMin(value = "2000",  message = "Amount must be at least 2000")
    @Schema(description = "Amount to top up", example = "100000", required = true)
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    @Schema(description = "Payment method (PAYOS)", example = "PAYOS", required = true)
    private String paymentMethod;

    @Schema(description = "Return URL after payment", example = "https://app.example.com/wallet/callback")
    private String returnUrl;

    @Schema(description = "Cancel URL if payment is cancelled", example = "https://app.example.com/wallet/cancel")
    private String cancelUrl;

    /**
     * ✅ FIX: Client-side idempotency key
     * Client tự generate UUID và gửi kèm để đảm bảo idempotency
     * Nếu không gửi, server sẽ tự generate (backward compatibility)
     */
    @Schema(description = "Idempotency key for duplicate request prevention. Client should generate UUID v4. If not provided, server will generate one.", 
            example = "550e8400-e29b-41d4-a716-446655440000")
    private String idempotencyKey;
}