package com.mssus.app.service;

import com.mssus.app.dto.response.notification.EmailRequest;
import com.mssus.app.dto.response.notification.EmailResult;

import java.util.concurrent.CompletableFuture;

public interface EmailService {
    CompletableFuture<EmailResult> sendVerificationEmail(String email, Long userId);
    CompletableFuture<EmailResult> sendWelcomeEmail(String email, String fullName);
    CompletableFuture<EmailResult> sendPasswordResetEmail(String email, String resetToken);
    EmailResult sendEmailSync(EmailRequest request);
}
