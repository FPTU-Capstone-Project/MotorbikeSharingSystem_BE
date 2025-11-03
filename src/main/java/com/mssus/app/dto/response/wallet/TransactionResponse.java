package com.mssus.app.dto.response.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Transaction details response")
public class TransactionResponse {

    @Schema(description = "Transaction ID", example = "12345")
    private Integer txnId;

    @Schema(description = "Group ID for related transactions", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID groupId;

    @Schema(description = "Transaction type", example = "TOPUP")
    private String type;

    @Schema(description = "Transaction direction (IN/OUT)", example = "IN")
    private String direction;

    @Schema(description = "Actor kind (USER/SYSTEM)", example = "USER")
    private String actorKind;

    @Schema(description = "Actor user ID", example = "1")
    private Integer actorUserId;

    @Schema(description = "Actor username", example = "john_doe")
    private String actorUsername;

    @Schema(description = "System wallet involved (if any)", example = "COMMISSION")
    private String systemWallet;

    @Schema(description = "Transaction amount", example = "100000")
    private BigDecimal amount;

    @Schema(description = "Currency code", example = "VND")
    private String currency;

    @Schema(description = "Shared ride ID", example = "456")
    private Long sharedRideId;

    private Long sharedRideRequestId;

    @Schema(description = "Payment service provider reference", example = "MOMO123456")
    private String pspRef;

    @Schema(description = "Transaction status", example = "COMPLETED")
    private String status;

    @Schema(description = "Available balance before transaction", example = "500000")
    private BigDecimal beforeAvail;

    @Schema(description = "Available balance after transaction", example = "600000")
    private BigDecimal afterAvail;

    @Schema(description = "Pending balance before transaction", example = "0")
    private BigDecimal beforePending;

    @Schema(description = "Pending balance after transaction", example = "0")
    private BigDecimal afterPending;

    @Schema(description = "Transaction created timestamp", example = "2025-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Additional transaction note", example = "Top-up via MoMo")
    private String note;
}