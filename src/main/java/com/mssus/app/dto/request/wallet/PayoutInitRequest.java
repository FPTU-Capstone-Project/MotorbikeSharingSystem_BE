package com.mssus.app.dto.request.wallet;

import com.mssus.app.dto.request.PayoutMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "Request to initiate a payout transaction")
public class PayoutInitRequest {


    @Schema(description = "Payout mode: MANUAL or AUTOMATIC")
    private PayoutMode mode = PayoutMode.MANUAL;

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

    @NotBlank(message = "Bank BIN is required")
    @Pattern(regexp = "^\\d{6}$", message = "Bank BIN must be 6 digits")
    @Schema(description = "Receiving bank BIN (6 digits)", example = "970415", required = true)
    private String bankBin;

    @Schema(description = "Optional payout categories reported to PayOS", example = "[\"payout\"]")
    private List<String> categories;
}