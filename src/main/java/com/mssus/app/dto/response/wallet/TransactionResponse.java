package com.mssus.app.dto.response.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Transaction details response")
public class TransactionResponse {

    @Schema(description = "Transaction ID", example = "12345")
    private Integer txnId;

    @Schema(description = "Transaction type", example = "TOPUP")
    private String type;

    @Schema(description = "Direction", example = "IN")
    private String direction;

    @Schema(description = "Amount", example = "100000")
    private BigDecimal amount;

    @Schema(description = "Currency", example = "VND")
    private String currency;

    @Schema(description = "Status", example = "COMPLETED")
    private String status;

    @Schema(description = "Balance before transaction", example = "500000")
    private BigDecimal beforeAvail;

    @Schema(description = "Balance after transaction", example = "600000")
    private BigDecimal afterAvail;

    @Schema(description = "Transaction note", example = "Top-up via MoMo")
    private String note;

    @Schema(description = "Transaction created at", example = "2025-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Booking ID (if applicable)", example = "456")
    private Long bookingId;

    @Schema(description = "PSP reference", example = "MOMO123456")
    private String pspRef;
}