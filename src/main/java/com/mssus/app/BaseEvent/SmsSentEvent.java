package com.mssus.app.BaseEvent;

public record SmsSentEvent(
    Long userId,
    String phoneNumber,
    String type,
    boolean success
) {}