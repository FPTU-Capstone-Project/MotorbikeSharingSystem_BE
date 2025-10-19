package com.mssus.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.dto.notification.DriverRideOfferNotification;
import com.mssus.app.dto.notification.RiderMatchStatusNotification;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

public interface RealTimeNotificationService {
    void notifyDriverOffer(DriverProfile driver, DriverRideOfferNotification payload);

    void notifyRiderStatus(User riderUser, RiderMatchStatusNotification payload);

    void notifyDriverJoinRequest(DriverProfile driver, DriverRideOfferNotification payload);

    void notifyDriverTrackingStart(DriverProfile driver, Integer rideId);
}
