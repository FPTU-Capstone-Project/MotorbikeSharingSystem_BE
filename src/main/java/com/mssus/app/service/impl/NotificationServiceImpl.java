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
import com.mssus.app.service.NotificationService;
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

@RequiredArgsConstructor
@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;

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
