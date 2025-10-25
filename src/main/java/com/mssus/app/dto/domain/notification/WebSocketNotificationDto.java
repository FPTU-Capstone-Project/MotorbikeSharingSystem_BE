package com.mssus.app.dto.domain.notification;

import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WebSocketNotificationDto {
    private Integer notificationId;
    private NotificationType type;
    private String title;
    private String message;
    private String payload;
    private Priority priority;
    private LocalDateTime sentAt;
    private boolean isRead;
}

