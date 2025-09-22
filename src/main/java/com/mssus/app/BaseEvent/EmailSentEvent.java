package com.mssus.app.BaseEvent;

public record EmailSentEvent(
    Long userId,
    String email,
    String type,
    boolean success
) {}