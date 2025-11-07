package com.mssus.app.dto.request;

import com.mssus.app.common.enums.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransactionRequest (
    UUID groupId,
    TransactionType type,
    TransactionDirection direction,
    ActorKind actorKind,
    Integer actorUserId,
    SystemWallet systemWallet,
    BigDecimal amount,
    String currency,
    Integer sharedRideId,
    Integer sharedRideRequestId,
    String pspRef,
    TransactionStatus status,
    String note,
    BigDecimal beforeAvail,
    BigDecimal afterAvail,
    BigDecimal beforePending,
    BigDecimal afterPending
) {}
