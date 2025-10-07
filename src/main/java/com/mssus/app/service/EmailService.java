package com.mssus.app.service;

import com.mssus.app.dto.response.notification.EmailPriority;
import com.mssus.app.dto.response.notification.EmailRequest;
import com.mssus.app.dto.response.notification.EmailResult;
import com.mssus.app.entity.User;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface EmailService {
    CompletableFuture<EmailResult> sendEmail(String email, String subject, String templateName,
                                             Map<String, Object> templateVars, EmailPriority priority,
                                             Long userId, String emailType);
    CompletableFuture<EmailResult> sendTopUpSuccessEmail(String email, String fullName, BigDecimal amount, String transactionId, BigDecimal newBalance);
    CompletableFuture<EmailResult> sendPaymentFailedEmail(String email, String fullName, BigDecimal amount, String transactionId, String reason);
    CompletableFuture<EmailResult> notifyDriverActivated(User user);
    CompletableFuture<EmailResult> notifyDriverSuspended(User user);
}
