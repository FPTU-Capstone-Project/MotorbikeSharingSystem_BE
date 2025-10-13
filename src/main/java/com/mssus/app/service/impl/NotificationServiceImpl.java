package com.mssus.app.service.impl;

import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.dto.notification.WebSocketNotificationDto;
import com.mssus.app.entity.Notification;
import com.mssus.app.entity.User;
import com.mssus.app.repository.NotificationRepository;
import com.mssus.app.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;

    @Override
    public void sendNotification(User user, NotificationType type, String title, String message, String payload,
                                 Priority priority, DeliveryMethod method, String queue) {
        String subPath;
        if (type == NotificationType.RIDE_REQUEST && queue != null && queue.equals("/queue/ride-offers")) {
            subPath = "/queue/ride-offers";
        } else if (type == NotificationType.RIDE_REQUEST && queue != null && queue.equals("/queue/ride-matching")) {
            subPath = "/queue/ride-matching";
        } else {
            subPath = "/queue/notifications";
        }

        String userIdStr = String.valueOf(user.getUserId());

        log.debug("Creating notification for user {} - title: {}, message: {}", userIdStr, title, message);

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

        WebSocketNotificationDto dto = WebSocketNotificationDto.builder()
            .notificationId(notification.getNotifId())
            .type(type)
            .title(title)
            .message(message)
            .payload(payload)
            .priority(priority)
            .sentAt(notification.getSentAt())
            .isRead(false)
            .build();

        log.info("Sending WebSocket notification to user {} via subPath: {}", userIdStr, subPath);
        try {
            messagingTemplate.convertAndSendToUser(userIdStr, subPath, dto);
            log.info("WebSocket notification sent successfully to user {}", userIdStr);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to user {}", userIdStr, e);
        }
    }

}
