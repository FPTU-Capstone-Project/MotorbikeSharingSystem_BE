package com.mssus.app.service;

import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.dto.response.notification.NotificationResponse;
import com.mssus.app.dto.response.notification.NotificationSummaryResponse;
import com.mssus.app.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface NotificationService {
    void sendNotification(User user, NotificationType type, String title, String message, String payload,
                          Priority priority, DeliveryMethod method, String queue);

    Page<NotificationSummaryResponse> getNotificationsForUser(Authentication authentication, Pageable pageable);

    NotificationResponse getNotificationById(Integer notifId);

    void markAsRead(Integer notifId);

    void markAllAsReadForUser(Authentication authentication);

    void deleteNotification(Integer notifId);

    void deleteAllForUser(Authentication authentication);
}
