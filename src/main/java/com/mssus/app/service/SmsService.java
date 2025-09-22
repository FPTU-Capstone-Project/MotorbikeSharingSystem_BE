package com.mssus.app.service;

import com.mssus.app.dto.request.SmsRequest;
import com.mssus.app.dto.response.notification.SmsResult;

import java.util.concurrent.CompletableFuture;

public interface SmsService {
    CompletableFuture<SmsResult> sendVerificationSms(String phoneNumber, Long userId);
    CompletableFuture<SmsResult> sendPasswordResetSms(String phoneNumber, String resetCode);
    SmsResult sendSmsSync(SmsRequest request);
}
