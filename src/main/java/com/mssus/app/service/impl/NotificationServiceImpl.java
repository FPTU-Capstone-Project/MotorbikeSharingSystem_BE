package com.mssus.app.service.impl;

import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.entity.Notification;
import com.mssus.app.entity.User;
import com.mssus.app.repository.NotificationRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
public class NotificationServiceImpl implements NotificationService {
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;

    @Override
    public void sendNotification(User user, NotificationType type, String title, String message, String payload,
                                 Priority priority, DeliveryMethod method) {
        String destination = "/user/" + user.getUserId() + "/queue/notifications";

        Notification notification = Notification.builder()
            .user(user)
            .type(type)
            .title(title)
            .message(message)
            .payload(payload)
            .priority(priority)
            .deliveryMethod(method)
            .isRead(false)
            .sentAt(LocalDateTime.now())
            .build();
        notificationRepository.save(notification);

        messagingTemplate.convertAndSend(destination, notification);
    }
}
