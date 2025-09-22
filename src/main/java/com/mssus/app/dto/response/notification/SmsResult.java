package com.mssus.app.dto.response.notification;

import java.math.BigDecimal;
import java.time.Instant;

public record SmsResult(
    boolean success,
    String messageId,
    String errorMessage,
    Instant sentAt,
    SmsProvider provider,
    BigDecimal cost
) {
    public static SmsResult success(String messageId, SmsProvider provider, BigDecimal cost) {
        return new SmsResult(true, messageId, null, Instant.now(), provider, cost);
    }

    public static SmsResult failure(String errorMessage, SmsProvider provider) {
        return new SmsResult(false, null, errorMessage, Instant.now(), provider, BigDecimal.ZERO);
    }
}