package com.mssus.app.service.impl;

import com.google.firebase.messaging.*;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.entity.FcmToken;
import com.mssus.app.entity.User;
import com.mssus.app.repository.FcmTokenRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmServiceImpl implements FcmService {

    private final FirebaseMessaging firebaseMessaging;
    private final FcmTokenRepository fcmTokenRepository;
    private final UserRepository userRepository;

    @Override
    public void sendPushNotification(Authentication authentication, String title, String body, Map<String, String> data) {
        Integer userId = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"))
            .getUserId();

        List<FcmToken> activeTokens = fcmTokenRepository.findByUserUserIdAndIsActive(userId, true);

        if (activeTokens.isEmpty()) {
            log.warn("No active FCM tokens found for user {}", userId);
            return;
        }

        List<String> tokens = activeTokens.stream()
            .map(FcmToken::getToken)
            .toList();

        MulticastMessage message = buildMulticastMessage(title, body, data, tokens);

        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
            handleResponse(response, tokens);

            log.info("Successfully sent {} FCM notifications to user {}",
                response.getSuccessCount(), userId);
        } catch (FirebaseMessagingException e) {
            log.error("Error sending FCM notification to user {}: {}", userId, e.getMessage(), e);
        }
    }

    @Override
    public void sendPushNotification(Integer userId, String title, String body, Map<String, String> data) {
        List<FcmToken> activeTokens = fcmTokenRepository.findByUserUserIdAndIsActive(userId, true);

        if (activeTokens.isEmpty()) {
            log.warn("No active FCM tokens found for user {}", userId);
            return;
        }

        List<String> tokens = activeTokens.stream()
            .map(FcmToken::getToken)
            .toList();

        MulticastMessage message = buildMulticastMessage(title, body, data, tokens);

        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
            handleResponse(response, tokens);

            log.info("Successfully sent {} FCM notifications to user {}",
                response.getSuccessCount(), userId);
        } catch (FirebaseMessagingException e) {
            log.error("Error sending FCM notification to user {}: {}", userId, e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void registerToken(Authentication authentication, String token, String deviceType) {
        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));

        Integer userId = user.getUserId();

        // Check if token already exists
        fcmTokenRepository.findByToken(token)
            .ifPresentOrElse(
                existingToken -> {
                    existingToken.setIsActive(true);
                    existingToken.setLastUsedAt(LocalDateTime.now());
                    fcmTokenRepository.save(existingToken);
                    log.info("Reactivated existing FCM token for user {}", userId);
                },
                () -> {
                    FcmToken newToken = FcmToken.builder()
                        .user(user)
                        .token(token)
                        .deviceType(deviceType)
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .lastUsedAt(LocalDateTime.now())
                        .build();
                    fcmTokenRepository.save(newToken);
                    log.info("Registered new FCM token for user {}", userId);
                }
            );
    }

    @Override
    @Transactional
    public void deactivateToken(String token) {
        fcmTokenRepository.findByToken(token)
            .ifPresent(fcmToken -> {
                fcmToken.setIsActive(false);
                fcmTokenRepository.save(fcmToken);
                log.info("Deactivated FCM token for user {}", fcmToken.getUser().getUserId());
            });
    }

    private MulticastMessage buildMulticastMessage(String title, String body,
                                                   Map<String, String> data, List<String> tokens) {
        Notification notification = Notification.builder()
            .setTitle(title)
            .setBody(body)
            .build();

        return MulticastMessage.builder()
            .setNotification(notification)
            .putAllData(data != null ? data : Map.of())
            .addAllTokens(tokens)
            .build();
    }

    private void handleResponse(BatchResponse response, List<String> tokens) {
        if (response.getFailureCount() > 0) {
            List<SendResponse> responses = response.getResponses();
            List<String> failedTokens = new ArrayList<>();

            for (int i = 0; i < responses.size(); i++) {
                if (!responses.get(i).isSuccessful()) {
                    failedTokens.add(tokens.get(i));

                    // Deactivate invalid tokens
                    FirebaseMessagingException exception = responses.get(i).getException();
                    if (exception != null && isTokenInvalid(exception)) {
                        deactivateToken(tokens.get(i));
                    }
                }
            }

            log.warn("Failed to send {} FCM notifications. Invalid tokens deactivated",
                failedTokens.size());
        }
    }

    private boolean isTokenInvalid(FirebaseMessagingException exception) {
        String errorCode = exception.getErrorCode().name();
        return "registration-token-not-registered".equals(errorCode) ||
            "invalid-registration-token".equals(errorCode) ||
            "invalid-argument".equals(errorCode);
    }
}
