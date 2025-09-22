package com.mssus.app.dto.response.notification;

import java.util.Map;

public record EmailRequest(
        String to,
        String subject,
        String templateName,
        Map<String, Object> templateVariables,
        EmailPriority priority,
        Long userId
) {
    public static EmailRequest verification(String email, Long userId, String verificationToken) {
        return new EmailRequest(
                email,
                "Verify your email address",
                "email-verification",
                Map.of(
                        "verificationToken", verificationToken,
                        "verificationUrl", buildVerificationUrl(verificationToken),
                        "userId", userId
                ),
                EmailPriority.HIGH,
                userId
        );
    }

    private static String buildVerificationUrl(String token) {
        return "${frontendBaseUrl}/verify-email?token=" + token;
    }
}
