package com.mssus.app.service.impl;

import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.notification.WebSocketNotificationDto;
import com.mssus.app.dto.response.notification.NotificationResponse;
import com.mssus.app.dto.response.notification.NotificationSummaryResponse;
import com.mssus.app.entity.Notification;
import com.mssus.app.entity.User;
import com.mssus.app.mapper.NotificationMapper;
import com.mssus.app.repository.NotificationRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.FcmService;
import com.mssus.app.service.NotificationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;
    private final FcmService fcmService;

    @Override
    @Transactional(dontRollbackOn = BaseDomainException.class)
    public void sendNotification(
        User recipient,
        NotificationType type,
        String title,
        String message,
        String payloadJson,
        Priority priority,
        DeliveryMethod method,
        String queue) {
        Notification notification = Notification.builder()
            .user(recipient)
            .type(type)
            .title(title)
            .message(message)
            .payload(payloadJson)
            .priority(priority)
            .deliveryMethod(method)
            .isRead(false)
            .sentAt(LocalDateTime.now())
            .build();
        // Save the notification first. It's now part of the transaction.
        notificationRepository.save(notification);
        log.info("Notification (method: {}) saved to database for user {}", method, recipient.getUserId());

        String subPath = "/queue/notifications";
        if (type == NotificationType.RIDE_REQUEST) {
            if ("/queue/ride-offers".equals(queue)) {
                subPath = "/queue/ride-offers";
            } else if ("/queue/ride-matching".equals(queue)) {
                subPath = "/queue/ride-matching";
            }
        }

        switch (method) {
            case IN_APP -> sendWebSocketNotification(recipient, notification, subPath);
            case PUSH -> sendPushNotification(recipient, notification, subPath);
            case EMAIL -> sendEmailNotification(recipient, title, message);
            case SMS -> sendSmsNotification(recipient, message);
        }
    }

    private void sendWebSocketNotification(User recipient, Notification notification, String subPath) {
        try {
            WebSocketNotificationDto dto = WebSocketNotificationDto.builder()
                .notificationId(notification.getNotifId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .payload(notification.getPayload())
                .priority(notification.getPriority())
                .sentAt(notification.getSentAt())
                .isRead(false)
                .build();

            messagingTemplate.convertAndSendToUser(
                recipient.getUserId().toString(),
                subPath,
                dto
            );
            log.info("WebSocket notification sent to user {} via {}", recipient.getUserId(), subPath);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to user {}: {}", recipient.getUserId(), e.getMessage(), e);
        }
    }

    private void sendPushNotification(User recipient, Notification notification, String webSocketFallbackPath) {
        try {
            Map<String, String> data = Map.of(
                "payload", notification.getPayload() != null ? notification.getPayload() : "{}"
            );
            fcmService.sendPushNotification(recipient.getUserId(), notification.getTitle(), notification.getMessage(), data);
            log.info("Push notification sent to user {}", recipient.getUserId());
        } catch (BaseDomainException e) {
            log.error("Failed to send push notification to user {}: {}. Falling back to WebSocket notification.",
                recipient.getUserId(), e.getMessage(), e);

            notification.setDeliveryMethod(DeliveryMethod.IN_APP);
            notificationRepository.save(notification);

            sendWebSocketNotification(recipient, notification, webSocketFallbackPath);
            log.info("Fallback WebSocket notification sent to user {}", recipient.getUserId());
        }
    }


    private void sendEmailNotification(User recipient, String title, String message) {
        // TODO: Implement actual email sending
        throw new UnsupportedOperationException("Email notification not implemented");
    }

    private void sendSmsNotification(User recipient, String message) {
        // TODO: Implement actual SMS sending
        throw new UnsupportedOperationException("SMS notification not implemented");
    }


    @Override
    public Page<NotificationSummaryResponse> getNotificationsForUser(Authentication authentication, Pageable pageable) {
        var userId = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"))
            .getUserId();
        List<Notification> notifications = notificationRepository.findByUserId(userId);
        Page<Notification> page;

        if (notifications.isEmpty()) {
            return Page.empty(pageable);
        }

        page = new PageImpl<>(notifications, pageable, notifications.size());

        return page.map(notificationMapper::toSummaryResponse);
    }

    @Override
    public NotificationResponse getNotificationById(Integer notifId) {
        Notification notification = notificationRepository.findById(notifId)
            .orElseThrow(() -> BaseDomainException.of("notification.not-found.by-id"));
        return notificationMapper.toResponse(notification);
    }

    @Override
    public void markAsRead(Integer notifId) {
        Notification notification = notificationRepository.findById(notifId)
            .orElseThrow(() -> BaseDomainException.of("notification.not-found.by-id"));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
    }

    @Override
    public void markAllAsReadForUser(Authentication authentication) {
        var userId = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"))
            .getUserId();
        var unreadNotifications = notificationRepository.findByUser_UserIdAndIsReadFalse(userId);

        if (unreadNotifications.isEmpty()) {
            return;
        }

        for (var notification : unreadNotifications) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
        }
        notificationRepository.saveAll(unreadNotifications);
    }

    @Override
    public void deleteNotification(Integer notifId) {
        Notification notification = notificationRepository.findById(notifId)
            .orElseThrow(() -> BaseDomainException.of("notification.not-found.by-id", String.valueOf(notifId)));
        notificationRepository.delete(notification);
    }

    @Override
    public void deleteAllForUser(Authentication authentication) {
        var userId = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"))
            .getUserId();
        var notifications = notificationRepository.findByUserId(userId);
        notificationRepository.deleteAll(notifications);
    }

}
