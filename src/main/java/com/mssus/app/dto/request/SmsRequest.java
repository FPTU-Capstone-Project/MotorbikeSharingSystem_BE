package com.mssus.app.dto.request;

import com.mssus.app.dto.response.notification.SmsType;

public record SmsRequest(
    String to,
    String message,
    SmsType type,
    Long userId
) {
    public static SmsRequest verification(String phoneNumber, Long userId, String verificationCode) {
        String message = String.format(
            "Your verification code is: %s. This code will expire in 10 minutes. Do not share this code with anyone.",
            verificationCode
        );
        return new SmsRequest(phoneNumber, message, SmsType.VERIFICATION, userId);
    }

    public static SmsRequest passwordReset(String phoneNumber, String resetCode) {
        String message = String.format(
            "Your password reset code is: %s. Use this code to reset your password. Code expires in 5 minutes.",
            resetCode
        );
        return new SmsRequest(phoneNumber, message, SmsType.PASSWORD_RESET, null);
    }
}