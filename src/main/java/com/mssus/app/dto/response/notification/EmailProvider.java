package com.mssus.app.dto.response.notification;

public interface EmailProvider {
    EmailResult sendEmail(EmailRequest request);
    String getName();
    int getPriority();
}
