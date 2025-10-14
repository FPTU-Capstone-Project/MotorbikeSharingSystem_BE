package com.mssus.app.service;

import org.springframework.security.core.Authentication;

import java.util.Map;

public interface FcmService {
    void sendPushNotification(Authentication authentication, String title, String body, Map<String, String> data);

    void sendPushNotification(Integer userId, String title, String body, Map<String, String> data);

    void registerToken(Authentication authentication, String token, String deviceType);

    void deactivateToken(String token);
}
