package com.mssus.app.dto.response.notification;

import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;

import java.time.LocalDateTime;

public record NotificationResponse(
    Integer notifId,
    NotificationType type,
    String title,
    String message,
    String payload,
    Priority priority,
    boolean isRead,
    LocalDateTime createdAt,
    LocalDateTime readAt,
    LocalDateTime sentAt,
    LocalDateTime expiresAt
) {
}
