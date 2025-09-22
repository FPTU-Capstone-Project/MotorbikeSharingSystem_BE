package com.mssus.app.dto.response.notification;

import com.mssus.app.dto.request.SmsRequest;

public interface SmsProvider {
    SmsResult sendSms(SmsRequest request);
    String getName();
    int getPriority();
}