package com.mssus.app.dto.response.notification;

import java.time.Instant;

public record EmailResult(
        boolean success,
        String messageId,
        String errorMessage,
        Instant sentAt,
        EmailProvider provider
) {
    public static EmailResult success(String messageId, EmailProvider provider) {
        return new EmailResult(true, messageId, null, Instant.now(), provider);
    }

    public static EmailResult failure(String errorMessage, EmailProvider provider) {
        return new EmailResult(false, null, errorMessage, Instant.now(), provider);
    }
}
