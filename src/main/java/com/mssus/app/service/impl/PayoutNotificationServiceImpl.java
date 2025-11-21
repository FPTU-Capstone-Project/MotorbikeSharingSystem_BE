package com.mssus.app.service.impl;

import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.entity.User;
import com.mssus.app.service.NotificationService;
import com.mssus.app.service.PayoutNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutNotificationServiceImpl implements PayoutNotificationService {

    private final NotificationService notificationService;

    @Override
    public void notifyPayoutInitiated(User user, String payoutRef, BigDecimal amount) {
        try {
            String title = "Yêu cầu rút tiền đã được tạo";
            String message = String.format("Yêu cầu rút tiền %s VNĐ đã được tạo thành công. Mã tham chiếu: %s", 
                    formatAmount(amount), payoutRef);
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("payoutRef", payoutRef);
            payload.put("amount", amount);
            payload.put("status", "PENDING");
            payload.put("type", "PAYOUT_INITIATED");

            notificationService.sendNotification(
                    user,
                    NotificationType.WALLET_PAYOUT,
                    title,
                    message,
                    toJsonString(payload),
                    Priority.MEDIUM,
                    DeliveryMethod.IN_APP,
                    "/queue/notifications");

            log.info("Payout initiated notification sent to user {}", user.getUserId());
        } catch (Exception e) {
            log.error("Failed to send payout initiated notification to user {}: {}", 
                    user.getUserId(), e.getMessage(), e);
        }
    }

    @Override
    public void notifyPayoutProcessing(User user, String payoutRef, BigDecimal amount) {
        try {
            String title = "Yêu cầu rút tiền đang được xử lý";
            String message = String.format("Yêu cầu rút tiền %s VNĐ (mã: %s) đang được xử lý. Vui lòng chờ trong giây lát.", 
                    formatAmount(amount), payoutRef);
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("payoutRef", payoutRef);
            payload.put("amount", amount);
            payload.put("status", "PROCESSING");
            payload.put("type", "PAYOUT_PROCESSING");

            notificationService.sendNotification(
                    user,
                    NotificationType.WALLET_PAYOUT,
                    title,
                    message,
                    toJsonString(payload),
                    Priority.MEDIUM,
                    DeliveryMethod.IN_APP,
                    "/queue/notifications");

            log.info("Payout processing notification sent to user {}", user.getUserId());
        } catch (Exception e) {
            log.error("Failed to send payout processing notification to user {}: {}", 
                    user.getUserId(), e.getMessage(), e);
        }
    }

    @Override
    public void notifyPayoutSuccess(User user, String payoutRef, BigDecimal amount) {
        try {
            String title = "Rút tiền thành công";
            String message = String.format("Yêu cầu rút tiền %s VNĐ (mã: %s) đã được xử lý thành công. Tiền đã được chuyển vào tài khoản của bạn.", 
                    formatAmount(amount), payoutRef);
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("payoutRef", payoutRef);
            payload.put("amount", amount);
            payload.put("status", "SUCCESS");
            payload.put("type", "PAYOUT_SUCCESS");

            notificationService.sendNotification(
                    user,
                    NotificationType.WALLET_PAYOUT,
                    title,
                    message,
                    toJsonString(payload),
                    Priority.HIGH,
                    DeliveryMethod.IN_APP,
                    "/queue/notifications");

            log.info("Payout success notification sent to user {}", user.getUserId());
        } catch (Exception e) {
            log.error("Failed to send payout success notification to user {}: {}", 
                    user.getUserId(), e.getMessage(), e);
        }
    }

    @Override
    public void notifyPayoutFailed(User user, String payoutRef, BigDecimal amount, String reason) {
        try {
            String title = "Rút tiền thất bại";
            String message = String.format("Yêu cầu rút tiền %s VNĐ (mã: %s) đã thất bại. %s. Số tiền đã được hoàn lại vào ví của bạn.", 
                    formatAmount(amount), payoutRef, reason != null ? reason : "Vui lòng thử lại sau");
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("payoutRef", payoutRef);
            payload.put("amount", amount);
            payload.put("status", "FAILED");
            payload.put("reason", reason);
            payload.put("type", "PAYOUT_FAILED");

            notificationService.sendNotification(
                    user,
                    NotificationType.WALLET_PAYOUT,
                    title,
                    message,
                    toJsonString(payload),
                    Priority.HIGH,
                    DeliveryMethod.IN_APP,
                    "/queue/notifications");

            log.info("Payout failed notification sent to user {}", user.getUserId());
        } catch (Exception e) {
            log.error("Failed to send payout failed notification to user {}: {}", 
                    user.getUserId(), e.getMessage(), e);
        }
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return String.format("%,d", amount.longValue());
    }

    private String toJsonString(Map<String, Object> payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize payload to JSON: {}", e.getMessage());
            return "{}";
        }
    }
}

