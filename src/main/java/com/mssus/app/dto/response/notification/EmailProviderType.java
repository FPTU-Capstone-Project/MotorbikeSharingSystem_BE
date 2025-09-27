package com.mssus.app.dto.response.notification;

public enum EmailProviderType {
    GMAIL_SMTP("Gmail SMTP"),
    SENDGRID("SendGrid"),
    AWS_SES("AWS SES"),
    OTHER("Other");

    private final String displayName;

    EmailProviderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}