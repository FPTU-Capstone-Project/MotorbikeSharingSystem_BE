package com.mssus.app.service;

import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.entity.User;

public interface NotificationService {
    void sendNotification(User user, NotificationType type, String title, String message, String payload,
                          Priority priority, DeliveryMethod method);
}
