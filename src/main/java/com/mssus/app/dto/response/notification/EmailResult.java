package com.mssus.app.dto.response.notification;

import java.time.Instant;

public record EmailResult(
        boolean success,
        String messageId,
        String errorMessage,
        Instant sentAt,
        EmailProviderType provider
) {
    public static EmailResult success(String messageId) {
        return new EmailResult(true, messageId, null, Instant.now(), EmailProviderType.GMAIL_SMTP);
    }

    public static EmailResult failure(String errorMessage) {
        return new EmailResult(false, null, errorMessage, Instant.now(), EmailProviderType.GMAIL_SMTP);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
