package com.mssus.app.dto.response.notification;

import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;

import java.time.LocalDateTime;

public record NotificationSummaryResponse(
    Integer notifId,
    String title,
    String message,
    boolean isRead,
    LocalDateTime createdAt,
    NotificationType type,
    Priority priority
    ) {}
