package com.mssus.app.dto.request.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request to initiate a payout transaction")
public class PayoutInitRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Schema(description = "Amount to withdraw", example = "500000", required = true)
    private BigDecimal amount;

    @NotBlank(message = "Bank account number is required")
    @Schema(description = "Bank account number", example = "1234567890", required = true)
    private String bankAccountNumber;

    @NotBlank(message = "Bank name is required")
    @Schema(description = "Bank name", example = "Vietcombank", required = true)
    private String bankName;

    @NotBlank(message = "Account holder name is required")
    @Schema(description = "Account holder name", example = "NGUYEN VAN A", required = true)
    private String accountHolderName;
}